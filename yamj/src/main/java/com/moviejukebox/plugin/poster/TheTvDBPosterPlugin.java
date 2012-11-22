/*
 *      Copyright (c) 2004-2012 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *
 *      For any reuse or distribution, you must make clear to others the
 *      license terms of this work.
 */
package com.moviejukebox.plugin.poster;

import com.moviejukebox.model.*;
import com.moviejukebox.plugin.TheTvDBPlugin;
import com.moviejukebox.plugin.TheTvDBPluginH;
import com.moviejukebox.tools.*;
import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.model.Banner;
import com.omertron.thetvdbapi.model.BannerType;
import com.omertron.thetvdbapi.model.Banners;
import com.omertron.thetvdbapi.model.Series;
import java.util.List;
import org.apache.log4j.Logger;
import org.pojava.datetime2.DateTime;

public class TheTvDBPosterPlugin implements ITvShowPosterPlugin {

    private static final Logger logger = Logger.getLogger(TheTvDBPosterPlugin.class);
    private static final String logMessage = "TheTvDBPosterPlugin: ";
    private static final String API_KEY = PropertiesUtil.getProperty("API_KEY_TheTVDb");
    private static final String DEFAULT_LANGUAGE = "en";
    private String language;
    private String language2nd;
    private TheTVDBApi tvDB;
    private static final String WEB_HOST = "thetvdb.com";
    private boolean isHibernateEnabled = Boolean.FALSE;

    public TheTvDBPosterPlugin() {
        super();

        // Check to see if we are needed
        if (!isNeeded()) {
            return;
        }

        tvDB = new TheTVDBApi(API_KEY);

        // We need to set the proxy parameters if set.
        tvDB.setProxy(WebBrowser.getMjbProxyHost(), WebBrowser.getMjbProxyPort(), WebBrowser.getMjbProxyUsername(), WebBrowser.getMjbProxyPassword());

        // Set the timeout values
        tvDB.setTimeout(WebBrowser.getMjbTimeoutConnect(), WebBrowser.getMjbTimeoutRead());

        language = PropertiesUtil.getProperty("thetvdb.language", DEFAULT_LANGUAGE);
        language2nd = PropertiesUtil.getProperty("thetvdb.language.secondary", DEFAULT_LANGUAGE);
        // We do not need use the same secondary language... So clearing when equal.
        if (language2nd.equalsIgnoreCase(language)) {
            language2nd = "";
        }

        if (PropertiesUtil.getProperty("mjb.internet.tv.plugin", "").endsWith("TheTvDBPluginH")) {
            isHibernateEnabled = Boolean.TRUE;
        }
    }

    @Override
    public String getIdFromMovieInfo(String title, String year, int tvSeason) {
        String response = Movie.UNKNOWN;
        List<Series> seriesList = null;

        if (StringTools.isValidString(title)) {
            ThreadExecutor.enterIO(WEB_HOST);
            try {
                seriesList = tvDB.searchSeries(title, language);
                // Try Alternative Language
                if ((seriesList == null || seriesList.isEmpty()) && StringTools.isValidString(language2nd)) {
                    seriesList = tvDB.searchSeries(title, language2nd);
                }
            } finally {
                ThreadExecutor.leaveIO();
            }
        }

        if (seriesList != null && !seriesList.isEmpty()) {
            Series series = null;
            for (Series s : seriesList) {
                if (s.getFirstAired() != null && !s.getFirstAired().isEmpty()) {
                    if (StringTools.isValidString(year)) {
                        try {
                            DateTime firstAired = DateTime.parse(s.getFirstAired());
                            if (Integer.parseInt(firstAired.toString("yyyy")) == Integer.parseInt(year)) {
                                series = s;
                                break;
                            }
                        } catch (Exception ignore) {
                        }
                    } else {
                        series = s;
                        break;
                    }
                }
            }

            if (series == null) {
                series = seriesList.get(0);
            }

            if (series != null) {
                response = String.valueOf(series.getId());
            }
        }

        return response;
    }

    @Override
    public IImage getPosterUrl(String id, int season) {
        String posterURL = Movie.UNKNOWN;

        ThreadExecutor.enterIO(WEB_HOST);

        try {
            if (!(id.equals(Movie.UNKNOWN) || (id.equals("-1"))) || (id.equals("0"))) {
                String urlNormal = null;

                Banners banners;
                // Ugly code for testing purposes
                if (isHibernateEnabled) {
                    banners = TheTvDBPluginH.getBanners(id);
                } else {
                    banners = TheTvDBPlugin.getBanners(id);
                }

                if (!banners.getSeasonList().isEmpty()) {
                    // Trying to grab localized banners at first...
                    urlNormal = findPosterURL(banners, season, language);
                    if (StringTools.isNotValidString(urlNormal) && !language2nd.isEmpty()) {
                        // In a case of failure - trying to grab banner in alternative language.
                        urlNormal = findPosterURL(banners, season, language2nd);
                    }
                }

                if (StringTools.isNotValidString(urlNormal) && !banners.getPosterList().isEmpty()) {
                    urlNormal = banners.getPosterList().get(0).getUrl();
                }

                if (urlNormal == null) {
                    Series series;
                    // Ugly code for testing purposes
                    if (isHibernateEnabled) {
                        series = TheTvDBPluginH.getSeries(id);
                    } else {
                        series = TheTvDBPlugin.getSeries(id);
                    }

                    if (series != null && StringTools.isValidString(series.getPoster())) {
                        urlNormal = series.getPoster();
                    }
                }

                if (urlNormal != null) {
                    posterURL = urlNormal;
                }
            }

            if (StringTools.isValidString(posterURL)) {
                logger.debug(logMessage + "Used poster " + posterURL);
                return new Image(posterURL);
            }
        } finally {
            ThreadExecutor.leaveIO();
        }

        return Image.UNKNOWN;
    }

    @Override
    public IImage getPosterUrl(String title, String year, int tvSeason) {
        return getPosterUrl(getIdFromMovieInfo(title, year, tvSeason), tvSeason);
    }

    @Override
    public final String getName() {
        return "thetvdb";
    }

    @Override
    public IImage getPosterUrl(Identifiable ident, IMovieBasicInformation movieInformation) {
        String id = getId(ident);

        if (StringTools.isNotValidString(id)) {
            if (movieInformation.isTVShow()) {
                id = getIdFromMovieInfo(movieInformation.getOriginalTitle(), movieInformation.getYear(), movieInformation.getSeason());
            }
            // Id found
            if (StringTools.isValidString(id)) {
                ident.setId(getName(), id);
            }
        }

        if (StringTools.isValidString(id)) {
            if (movieInformation.isTVShow()) {
                return getPosterUrl(id, movieInformation.getSeason());
            }

        }
        return Image.UNKNOWN;
    }

    private String getId(Identifiable ident) {
        String response = Movie.UNKNOWN;
        if (ident != null) {
            String id = ident.getId(this.getName());
            if (id != null) {
                response = id;
            }
        }
        return response;
    }

    private String findPosterURL(final Banners bannerList, final int season, final String languageId) {
        for (Banner banner : bannerList.getSeasonList()) {
            if ((banner.getSeason() == season)
                    && (banner.getBannerType2() == BannerType.Season)
                    && (banner.getLanguage().equalsIgnoreCase(languageId))) {
                return banner.getUrl();
            }
        }
        return null;
    }

    @Override
    public final boolean isNeeded() {
        String searchPriority = PropertiesUtil.getProperty("poster.scanner.SearchPriority.tv", "");
        return searchPriority.toLowerCase().contains(this.getName());
    }
}
