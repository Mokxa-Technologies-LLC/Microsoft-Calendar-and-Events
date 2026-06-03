package org.joget.mokxa;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(CreateOrUpdateEvent.class.getName(), new CreateOrUpdateEvent(), null));
        registrationList.add(context.registerService(LoadOrSyncEvent.class.getName(), new LoadOrSyncEvent(), null));
        registrationList.add(context.registerService(EventList.class.getName(), new EventList(), null));
        registrationList.add(context.registerService(CancelEvent.class.getName(), new CancelEvent(), null));
        registrationList.add(context.registerService(CalendarList.class.getName(), new CalendarList(), null));
        registrationList.add(context.registerService(SyncTenantCalendarEventsTool.class.getName(), new SyncTenantCalendarEventsTool(), null));
        registrationList.add(context.registerService(SendCalendarEventReminderTool.class.getName(), new SendCalendarEventReminderTool(), null));

        registrationList.add(context.registerService(GraphApiConnectMenu.class.getName(), new GraphApiConnectMenu(), null));
        registrationList.add(context.registerService(MsGraphTokenRefreshTool.class.getName(), new MsGraphTokenRefreshTool(), null));


    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}