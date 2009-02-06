/*
 Plugin to retrieve movie data from Russian movie database www.kinopoisk.ru
 Written by Yury Sidorov.
  
 First the movie data is searched in IMDB and TheTvDB.
 After that the movie is searched in kinopoisk and movie data 
 is updated.
 
 It is possible to specify URL of the movie page on kinopoisk in 
 the .nfo file. In this case movie data will be retrieved from this page only.  
*/

package com.moviejukebox.plugin;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.StringTokenizer;


import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;

public class KinopoiskPlugin extends ImdbPlugin {

    public static String KINOPOISK_PLUGIN_ID = "kinopoisk";
    //Define plot length
    int preferredPlotLength = Integer.parseInt(PropertiesUtil.getProperty("kinopoisk.plot.maxlength", "400"));
    String preferredRating = PropertiesUtil.getProperty("kinopoisk.rating", "imdb");
    protected TheTvDBPlugin tvdb;

    public KinopoiskPlugin() {
        super();
        preferredCountry = PropertiesUtil.getProperty("imdb.preferredCountry", "Russia");
        tvdb = new TheTvDBPlugin(); 
    }

    @Override
    public boolean scan(Movie mediaFile) {
        boolean retval = true;
        String kinopoiskId = mediaFile.getId(KINOPOISK_PLUGIN_ID);
        if (kinopoiskId == null || kinopoiskId.equalsIgnoreCase(Movie.UNKNOWN)) { 
            // Get base info from imdb or tvdb
            if (!mediaFile.isTVShow())  
            	super.scan(mediaFile);
            else
            	tvdb.scan(mediaFile);
                
            kinopoiskId = getKinopoiskId(mediaFile.getTitle(), mediaFile.getYear(), mediaFile.getSeason());
            mediaFile.setId(KINOPOISK_PLUGIN_ID, kinopoiskId);
        }
        else {
        	// If ID is specified in NFO, set original title to unknown
        	mediaFile.setTitle(Movie.UNKNOWN);
        }
        if (kinopoiskId != null && !kinopoiskId.equalsIgnoreCase(Movie.UNKNOWN)) 
        {
        	// Replace some movie data by data from Kinopoisk 
            retval = updateKinopoiskMediaInfo(mediaFile, kinopoiskId);
        }
        return retval;
    }

    @Override
    public void scanNFO(String nfo, Movie movie) {
        logger.finest("Scanning NFO for Kinopoisk Id");
        int beginIndex = nfo.indexOf("kinopoisk.ru/level/1/film/");
        if (beginIndex != -1) {
            StringTokenizer st = new StringTokenizer(nfo.substring(beginIndex + 26), "/");
            movie.setId(KinopoiskPlugin.KINOPOISK_PLUGIN_ID, st.nextToken());
            logger.finer("Kinopoisk Id found in nfo = " + movie.getId(KinopoiskPlugin.KINOPOISK_PLUGIN_ID));
        } else {
            logger.finer("No Kinopoisk Id found in nfo !");
        }
        super.scanNFO(nfo, movie);
    }
    
    /**
     * retrieve Kinopoisk matching the specified movie name and year. This routine is base on a google
     * request.
     */
    private String getKinopoiskId(String movieName, String year, int season) {
        try {
            StringBuffer sb = new StringBuffer("http://www.google.ru/search?hl=ru&q=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));
            if (season != -1) {
                sb.append(URLEncoder.encode("+сериал", "UTF-8"));
            }
            if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
                sb.append("+").append(year);
            }

            sb.append("+site%3Awww.kinopoisk.ru");

            String xml = webBrowser.request(sb.toString());
            int beginIndex = xml.indexOf("kinopoisk.ru/level/1/film/");
            StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 26), "/\"");
            String kinopoiskId = st.nextToken();

            if (kinopoiskId != "") {
                return kinopoiskId;
            } else {
                return Movie.UNKNOWN;
            }

        } catch (Exception e) {
            logger.severe("Failed retreiving Kinopoisk Id for movie : " + movieName);
            logger.severe("Error : " + e.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * Scan Kinopoisk html page for the specified movie
     */
    private boolean updateKinopoiskMediaInfo(Movie movie, String kinopoiskId) {
        try {
            String xml = webBrowser.request("http://www.kinopoisk.ru/level/1/film/" + kinopoiskId);
            
            // Title
            if (!movie.isOverrideTitle()) {
            	String newTitle = HTMLTools.extractTag(xml, "class=\"moviename-big\">", 0, "<>");
            	if (!newTitle.equals(Movie.UNKNOWN)) {
            		int i = newTitle.indexOf("(сериал");
            		if (i >= 0) {
            			newTitle = newTitle.substring(0, i);
            			movie.setMovieType(Movie.TYPE_TVSHOW);
            		}
        			newTitle = newTitle.replace('\u00A0', ' ').trim();
            		if (movie.getSeason() != -1)
            			newTitle = newTitle + ", сезон " + String.valueOf(movie.getSeason()); 
            		// Original title is known and is not cyrillic?
            		i = movie.getTitle().codePointAt(0);
            		if (!movie.getTitle().equalsIgnoreCase(Movie.UNKNOWN) && (i < 0x400 || i >= 0x5B0))
            			// Add original title
            			newTitle = newTitle + " / " + movie.getTitle();
                    movie.setTitle(newTitle);
            	}
            }
            
            // Plot
            String plot = HTMLTools.extractTag(xml, "<td colspan=3 style=\"padding:10px;padding-left:20px;\" class=\"news\">", 0, "<>");
            if (plot.equals(Movie.UNKNOWN))
            	plot = movie.getPlot();
        	if (plot.length() > preferredPlotLength) 
        		plot = plot.substring(0, preferredPlotLength) + "...";
        	movie.setPlot(plot);
            
            // Genres
            LinkedList<String> newGenres = new LinkedList<String>();
            for (String genre : HTMLTools.extractTags(xml, ">жанр</td>", "</td>", "<a href=\"/level/10", "</a>")) {
            	genre = genre.substring(0, 1).toUpperCase() + genre.substring(1, genre.length());
            	if (genre.equalsIgnoreCase("мультфильм"))
            		newGenres.addFirst(genre);
            	else
            		newGenres.add(genre);
            }
            if (newGenres.size() > 0) {
            	movie.setGenres(newGenres);
            }
            
            // Director
            for (String director : HTMLTools.extractTags(xml, ">режиссер</td>", "</td>", "<a href=\"/level/4", "</a>")) {
                movie.setDirector(director);
                break;
            }
            
            // Cast
            Collection<String> newCast = new ArrayList<String>();
            for (String actor : HTMLTools.extractTags(xml, ">В главных ролях:", "</table>", "<a href=\"/level/4", "</a>")) {
            	newCast.add(actor);
            }
            if (newCast.size() > 0)
            	movie.setCast(newCast);

            // Country
            for (String country : HTMLTools.extractTags(xml, ">страна</td>", "</td>", "<a href=\"/level/10", "</a>")) {
                movie.setCountry(country);
                break;
            }
            
            // Year
            if (movie.getYear().equals(Movie.UNKNOWN)) {
            	for (String year : HTMLTools.extractTags(xml, ">год</td>", "</td>", "<a href=\"/level/10", "</a>")) {
            		movie.setYear(year);
            		break;
            	}
            }
            
            // Rating
            String rating = HTMLTools.extractTag(xml, "<a href=\"/level/83/film/"+kinopoiskId+"/\" class=\"continue\">", 0, "<");
            if (!rating.equals(Movie.UNKNOWN)) {
                int r = (int)(Float.parseFloat(rating)*10);
            	if (movie.getRating() != -1) { 
                    if (preferredRating.equals("imdb"))
                    	r = movie.getRating();
                    else
                        if (preferredRating.equals("average"))
                        	r = (r + movie.getRating())/2;
            	}
          		movie.setRating(r);
            }
            
            // Poster
            if (movie.getPosterURL() == null || movie.getPosterURL().equalsIgnoreCase(Movie.UNKNOWN)) {
                movie.setPosterURL(getPosterURL(movie, ""));
            }
            
            // Run time
            if (movie.getRuntime().equals(Movie.UNKNOWN)) {
                movie.setRuntime(getPreferredValue(HTMLTools.extractTags(xml, ">время</td>", "</td>", "<td", "</td>")));
            }
            
        } catch (Exception e) {
            logger.severe("Failed retreiving movie data from Kinopoisk : " + kinopoiskId);
            e.printStackTrace();
        }
        return true;
    }
}
