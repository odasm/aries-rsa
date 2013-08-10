/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.dosgi.dsw.decorator;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceDecoratorImpl implements ServiceDecorator {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceDecoratorImpl.class);

    final List<Rule> decorations = new CopyOnWriteArrayList<Rule>();

    public void decorate(ServiceReference sref, Map<String, Object> target) {
        for (Rule matcher : decorations) {
            matcher.apply(sref, target);
        }
    }

    @SuppressWarnings("unchecked")
    void addDecorations(Bundle bundle) {
        Namespace ns = Namespace.getNamespace("http://cxf.apache.org/xmlns/service-decoration/1.0.0");
        for (Element decoration : getDecorationElements(bundle)) {
            for (Element match : (List<Element>) decoration.getChildren("match", ns)) {
                InterfaceRule m = new InterfaceRule(bundle, match.getAttributeValue("interface"));
                for (Element propMatch : (List<Element>) match.getChildren("match-property", ns)) {
                    m.addPropMatch(propMatch.getAttributeValue("name"), propMatch.getAttributeValue("value"));
                }
                for (Element addProp : (List<Element>) match.getChildren("add-property", ns)) {
                    m.addProperty(addProp.getAttributeValue("name"),
                                  addProp.getAttributeValue("value"),
                                  addProp.getAttributeValue("type", String.class.getName()));
                }
                decorations.add(m);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static List<Element> getDecorationElements(Bundle bundle) {
        Enumeration entries = bundle.findEntries("OSGI-INF/remote-service", "*.xml", false);
        return getDecorationElementsForEntries(entries);
    }

    /**
     * Only for tests
     * @param entries
     * @return
     */
    @SuppressWarnings("unchecked")
    static List<Element> getDecorationElementsForEntries(Enumeration<URL> entries) {
        if (entries == null) {
            return Collections.emptyList();
        }
        List<Element> elements = new ArrayList<Element>();
        while (entries.hasMoreElements()) {
            URL resourceURL = entries.nextElement();
            try {
                Document d = new SAXBuilder().build(resourceURL.openStream());
                Namespace ns = Namespace.getNamespace("http://cxf.apache.org/xmlns/service-decoration/1.0.0");
                elements.addAll(d.getRootElement().getChildren("service-decoration", ns));
            } catch (Exception ex) {
                LOG.warn("Problem parsing: " + resourceURL, ex);
            }
        }
        return elements;
    }

    void removeDecorations(Bundle bundle) {
        for (Rule r : decorations) {
            if (bundle.equals(r.getBundle())) {
                decorations.remove(r); // the iterator doesn't support 'remove'
            }
        }
    }
}
