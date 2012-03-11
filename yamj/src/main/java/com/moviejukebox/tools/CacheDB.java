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
package com.moviejukebox.tools;

import java.io.Serializable;
import java.util.Collection;
import org.apache.log4j.Logger;

/**
 * Utility class to provide a caching mechanism for data across threads
 * Initially this will be stored in memory, but ideally should be cached to a
 * database Many sites provide a "last modified" date/time attribute, so we
 * should consider also caching that in the database
 *
 * @author Stuart.Boston
 *
 */
public class CacheDB {

    private static Logger logger = Logger.getLogger(CacheDB.class);
    private static boolean cacheEnabled = initCacheState();

    public static boolean initCacheState() {
        boolean isEnabled = PropertiesUtil.getBooleanProperty("mjb.cache", "true");
        logger.debug("Cache state is " + (isEnabled ? "enabled" : "disabled"));
        return isEnabled;
    }

    /**
     * Add an item to the cache. If the item currently exists in the cache it
     * will be removed before being added.
     *
     * @param key
     * @param value
     */
    public static void addToCache(Serializable key, Object value) {
        if (!cacheEnabled) {
            return;
        }

        boolean isSaved = HibernateUtil.saveObject(value, key);
        if (isSaved) {
            logger.debug("Cache (Add): Adding object (" + value.getClass().getSimpleName() + ") for key " + key);
        } else {
            logger.debug("Cache (Add): Already contains object (" + value.getClass().getSimpleName() + ") with key " + key + " overwriting...");
        }
    }
    
    public static <T> void addToCache(Serializable key, Collection<T> values) {
        if (!cacheEnabled) {
            return;
        }
        
        boolean isSaved = HibernateUtil.saveCollection(values,key);
        if (isSaved) {
            logger.debug("Cache (Add): Adding object (" + values.getClass().getSimpleName() + ") for key " + key);
        } else {
            logger.debug("Cache (Add): Already contains object (" + values.getClass().getSimpleName() + ") with key " + key + " overwriting...");
        }
    }

    /**
     * Get an item from the cache
     *
     * @param key
     * @return
     */
    public static <T> T getFromCache(Serializable key, Class<T> clazz) {
        if (!cacheEnabled) {
            return null;
        }

        T dbObject = HibernateUtil.loadObject(clazz, key);

        if (dbObject != null) {
            logger.debug("Cache (Get): Got object (" + clazz.getSimpleName() + ") for " + key);
        } else {
            logger.debug("Cache (Get): No object found for " + key);
        }

        return dbObject;
    }
}
