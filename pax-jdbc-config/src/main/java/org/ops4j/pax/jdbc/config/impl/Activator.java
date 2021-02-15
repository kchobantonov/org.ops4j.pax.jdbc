/*
 * Copyright 2021 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.jdbc.config.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.sql.CommonDataSource;

import org.ops4j.pax.jdbc.config.ConfigLoader;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {

    private static final String FACTORY_PID = "org.ops4j.datasource";

    private ServiceTracker<?, ?> dataSourceTracker;

    private ExternalConfigLoader externalConfigLoader;
    private ServiceRegistration<ConfigLoader> configLoaderRegistration;
    private DataSourceConfigManager configManager;
    private ServiceRegistration<ManagedServiceFactory> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        configLoaderRegistration = context.registerService(ConfigLoader.class, new FileConfigLoader(), new Hashtable<>());
        externalConfigLoader = new ExternalConfigLoader(context);
        Dictionary<String, String> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, FACTORY_PID);
        configManager = new DataSourceConfigManager(context, externalConfigLoader);
        // this service will track:
        //  - org.ops4j.datasource factory PIDs
        //  - (optionally) org.jasypt.encryption.StringEncryptor services
        //  - (optionally) org.ops4j.pax.jdbc.hook.PreHook services
        //  - org.osgi.service.jdbc.DataSourceFactory services
        registration = context.registerService(ManagedServiceFactory.class, configManager, props);

        // this service will track:
        //  - javax.sql.DataSource services
        //  - javax.sql.XADataSource services
        // and when they're registered:
        //  - with "pool=<pool name>"
        //  - without "pax.jdbc.managed=true"
        // they'll be processed by selected org.ops4j.pax.jdbc.pool.common.PooledDataSourceFactory
        // (as with org.ops4j.datasource factory PIDs)
        ServiceTrackerHelper helper = ServiceTrackerHelper.helper(context);
        String filter = "(&(pool=*)(!(pax.jdbc.managed=true))" +
                "(|(objectClass=javax.sql.DataSource)(objectClass=javax.sql.XADataSource)))";
        dataSourceTracker = helper.track(CommonDataSource.class, filter,
                (ds, reference) -> new DataSourceWrapper(context, externalConfigLoader, ds, reference),
                DataSourceWrapper::close
        );
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (dataSourceTracker != null) {
            dataSourceTracker.close();
        }
        registration.unregister();
        configManager.destroy();
        configLoaderRegistration.unregister();
        externalConfigLoader.destroy();
    }

}
