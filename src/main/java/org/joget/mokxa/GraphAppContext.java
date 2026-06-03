package org.joget.mokxa;

import org.joget.apps.app.service.AppUtil;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

public class GraphAppContext {

    private static GraphAppContext instance;
    private final GenericXmlApplicationContext springApplicationContext;

    public synchronized static GraphAppContext getInstance() {
        if (instance == null) {
            instance = new GraphAppContext();
        }
        return instance;
    }

    private GraphAppContext() {

        this.springApplicationContext = new GenericXmlApplicationContext();
        this.springApplicationContext.setValidating(false);
        this.springApplicationContext.setClassLoader(this.getClass().getClassLoader());
        this.springApplicationContext.setParent(AppUtil.getApplicationContext());
        this.springApplicationContext.load("/graphApplicationContext.xml");
        this.springApplicationContext.refresh();
    }

    public AbstractApplicationContext getAppContext() {
        return springApplicationContext;
    }
}
