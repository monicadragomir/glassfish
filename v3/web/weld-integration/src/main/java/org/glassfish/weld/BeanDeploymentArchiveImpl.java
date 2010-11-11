/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.weld;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.weld.ejb.EjbDescriptorImpl;
import org.jboss.weld.bootstrap.WeldBootstrap;
import org.jboss.weld.bootstrap.api.ServiceRegistry;
import org.jboss.weld.bootstrap.api.helpers.SimpleServiceRegistry;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.BeansXml;
import org.jboss.weld.ejb.spi.EjbDescriptor;

import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.InjectionTarget;


/*
 * The means by which Weld Beans are discovered on the classpath. 
 */
public class BeanDeploymentArchiveImpl implements BeanDeploymentArchive {
   
    private Logger logger = Logger.getLogger(BeanDeploymentArchiveImpl.class.getName());

    private static final char SEPARATOR_CHAR = '/';
    private static final String WEB_INF = "WEB-INF";
    private static final String WEB_INF_CLASSES = WEB_INF + SEPARATOR_CHAR + "classes";
    private static final String WEB_INF_LIB = WEB_INF + SEPARATOR_CHAR + "lib";
    private static final String WEB_INF_BEANS_XML = "WEB-INF" + SEPARATOR_CHAR + "beans.xml";
    private static final String META_INF_BEANS_XML = "META-INF" + SEPARATOR_CHAR + "beans.xml";
    private static final String CLASS_SUFFIX = ".class";
    private static final String JAR_SUFFIX = ".jar";

    private ReadableArchive archive;
    private String id;
    private List<Class<?>> wClasses = null;
    private List<URL> wUrls = null;
    private final Collection<EjbDescriptor<?>> ejbDescImpls;
    private List<BeanDeploymentArchive> beanDeploymentArchives;

    private SimpleServiceRegistry simpleServiceRegistry = null;

    public static final String WAR = "WAR";
    public static final String JAR = "JAR";
    public String bdaType;

    private DeploymentContext context;
    private final Map<AnnotatedType<?>, InjectionTarget<?>> itMap = new HashMap<AnnotatedType<?>, InjectionTarget<?>>();


    /**
     * Produce a <code>BeanDeploymentArchive</code> form information contained 
     * in the provided <code>ReadableArchive</code>.
     * @param context 
     */
    public BeanDeploymentArchiveImpl(ReadableArchive archive,
        Collection<com.sun.enterprise.deployment.EjbDescriptor> ejbs, DeploymentContext ctx) {
        this.wClasses = new ArrayList<Class<?>>();
        this.wUrls = new ArrayList<URL>();
        this.archive = archive;
        this.id = archive.getURI().getPath(); 
        this.ejbDescImpls = new HashSet<EjbDescriptor<?>>();
        this.beanDeploymentArchives = new ArrayList<BeanDeploymentArchive>();
        this.context = ctx;

        for(com.sun.enterprise.deployment.EjbDescriptor next : ejbs) {
            EjbDescriptorImpl wbEjbDesc = new EjbDescriptorImpl(next);
            ejbDescImpls.add(wbEjbDesc);
        }
        populate();
        try {
            this.archive.close();
        } catch (Exception e) {
        }
        this.archive = null;
    }

    public BeanDeploymentArchiveImpl(String id, List<Class<?>> wClasses, List<URL> wUrls,
        Collection<com.sun.enterprise.deployment.EjbDescriptor> ejbs, DeploymentContext ctx) {
        this.id = id;
        this.wClasses = wClasses;
        this.wUrls = wUrls;
        this.ejbDescImpls = new HashSet<EjbDescriptor<?>>();
        this.beanDeploymentArchives = new ArrayList<BeanDeploymentArchive>();
        this.context = ctx;

        for(com.sun.enterprise.deployment.EjbDescriptor next : ejbs) {
            EjbDescriptorImpl wbEjbDesc = new EjbDescriptorImpl(next);
            ejbDescImpls.add(wbEjbDesc);
        }
    }


    public Collection<BeanDeploymentArchive> getBeanDeploymentArchives() {
        return beanDeploymentArchives;
    }

    public Collection<String> getBeanClasses() {
        //TODO
        List<String> s  = new ArrayList<String>();
        for (Iterator<Class<?>> iterator = wClasses.iterator(); iterator.hasNext();) {
            String classname = iterator.next().getName();
            s.add(classname);
        }
        return s;
    }
    
    public Collection<Class<?>> getBeanClassObjects(){
        return wClasses;
    }

    public BeansXml getBeansXml() {
        WeldBootstrap wb =  context.getTransientAppMetaData(WeldDeployer.WELD_BOOTSTRAP, WeldBootstrap.class);
        return wb.parse(wUrls);
    }

    /**
    * Gets a descriptor for each EJB
    *
    * @return the EJB descriptors
    */
    public Collection<EjbDescriptor<?>> getEjbs() {

       return ejbDescImpls;
    }

    public EjbDescriptor getEjbDescriptor(String ejbName) {
        EjbDescriptor match = null;

        for(EjbDescriptor next : ejbDescImpls) {
            if( next.getEjbName().equals(ejbName) ) {
                match = next;
                break;
            }
        }

        return match;
    }

    public ServiceRegistry getServices() {
        if (simpleServiceRegistry == null) {
            simpleServiceRegistry = new SimpleServiceRegistry();
        }
        return simpleServiceRegistry;
    }

    public String getId() {
        return id;
    }

    public String toString() {
        String val = "ID: "+getId()+" CLASSES: "+getBeanClasses()+"\n"; 
        Collection <BeanDeploymentArchive> bdas = getBeanDeploymentArchives();
        Iterator<BeanDeploymentArchive> iter = bdas.iterator();
        while (iter.hasNext()) {
            BeanDeploymentArchive bda = (BeanDeploymentArchive)iter.next();
            val += "   ID: "+bda.getId()+" CLASSES: "+bda.getBeanClasses();
        }
        return val;
    }

    public String getBDAType() {
        return bdaType;
    }

    private void populate() {
        try {
            if (archive.exists(WEB_INF_BEANS_XML)) {
                bdaType = WAR;
                Enumeration<String> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement();
                    if (entry.endsWith(CLASS_SUFFIX)) {
                        entry = entry.substring(WEB_INF_CLASSES.length()+1);
                        String className = filenameToClassname(entry);
                        wClasses.add(getClassLoader().loadClass(className));
                    } else if (entry.endsWith("beans.xml")) {
                        URI uri = archive.getURI();
                        File file = new File(uri.getPath() + entry);
                        URL beansXmlUrl = file.toURI().toURL();
                        wUrls.add(beansXmlUrl);
                    }
                }
                archive.close();
            }

            // If this archive has WEB-INF/lib entry..
            // Examine all jars;  If the examined jar has a META_INF/beans.xml:
            //  collect all classes in the jar archive
            //  beans.xml in the jar archive

            if (archive.exists(WEB_INF_LIB)) {
                bdaType = WAR;
                Enumeration<String> entries = archive.entries(WEB_INF_LIB);
                while (entries.hasMoreElements()) {
                    String entry = (String)entries.nextElement();
                    if (entry.endsWith(JAR_SUFFIX) &&
                        entry.indexOf(SEPARATOR_CHAR, WEB_INF_LIB.length() + 1 ) == -1 ) {
                        ReadableArchive jarArchive = archive.getSubArchive(entry);
                        if (jarArchive.exists(META_INF_BEANS_XML)) {
                            collectJarInfo(jarArchive);
                        }
                    }
               }
            }

            if (archive.exists(META_INF_BEANS_XML)) {
                bdaType = JAR;
                collectJarInfo(archive);
            }
        } catch(IOException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        } catch(ClassNotFoundException cne) {
            logger.log(Level.SEVERE, cne.getLocalizedMessage(), cne);
        }
    }   

    private void collectJarInfo(ReadableArchive archive) throws IOException, ClassNotFoundException {
        Enumeration<String> entries = archive.entries();
        while (entries.hasMoreElements()) {
            String entry = entries.nextElement();
            if (entry.endsWith(CLASS_SUFFIX)) {
                String className = filenameToClassname(entry);
                wClasses.add(getClassLoader().loadClass(className));
            } else if (entry.endsWith("beans.xml")) {
                URL beansXmlUrl = Thread.currentThread().getContextClassLoader().getResource(entry);
                wUrls.add(beansXmlUrl);
            }
        }
    }

    private static String filenameToClassname(String filename) {
        String className = null;
        if (filename.indexOf(File.separatorChar) >= 0) {
            className = filename.replace(File.separatorChar, '.');
        } else {
            className = filename.replace(SEPARATOR_CHAR, '.');
        }
        className = className.substring(0, className.length()-6);
        return className;
    }

    private ClassLoader getClassLoader() {
        if (Thread.currentThread().getContextClassLoader() != null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return DeploymentImpl.class.getClassLoader();
        }
    }

    public InjectionTarget<?> getInjectionTarget(AnnotatedType<?> annotatedType) {
        return itMap.get(annotatedType);
    }

    void putInjectionTarget(AnnotatedType<?> annotatedType, InjectionTarget<?> it) {
        itMap.put(annotatedType, it);
    }

}
