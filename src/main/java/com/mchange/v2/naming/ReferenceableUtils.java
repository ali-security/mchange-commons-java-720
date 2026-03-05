/*
 * Distributed as part of mchange-commons-java 0.2.11
 *
 * Copyright (C) 2015 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.naming;

import java.net.*;
import java.util.*;
import javax.naming.*;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import javax.naming.spi.ObjectFactory;

public final class ReferenceableUtils
{
    final static MLogger logger = MLog.getLogger( ReferenceableUtils.class );

    /* don't worry -- References can have duplicate RefAddrs (I think!) */
    final static String REFADDR_VERSION                = "version";
    final static String REFADDR_CLASSNAME              = "classname";
    final static String REFADDR_FACTORY                = "factory";
    final static String REFADDR_FACTORY_CLASS_LOCATION = "factoryClassLocation";
    final static String REFADDR_SIZE                   = "size";

    final static int CURRENT_REF_VERSION = 1;

    /**
     * A null string value in a Reference sometimes goes to the literal
     * "null". Sigh. We convert this string to a Java null.
     */
    public static String literalNullToNull( String s )
    {
	if (s == null || "null".equals( s ))
	    return null;
	else
	    return s;
    }

    public static Object referenceToObject( Reference ref, Name name, Context nameCtx, Hashtable env )
	throws NamingException
    { return referenceToObject( ref, name, nameCtx, env, (MultiPropertiesConfig) null ); }

    public static Object referenceToObject( Reference ref, Name name, Context nameCtx, Hashtable env, MultiPropertiesConfig mcfg )
	throws NamingException
    {
	try
	    {
		String fClassName = ref.getFactoryClassName();
		String fClassLocation = ref.getFactoryClassLocation();

		// Reject references with no factory class name; we do not implement
		// the NamingManager fallback conventions for null factoryClassName.
		if (fClassName == null)
		    throw new NamingException(
			"A null factoryClassName was encountered. " +
			"ReferenceableUtils.referenceToObject(...) does not support null factory class names. " +
			"If the null is intentional, consider using javax.naming.spi.NamingManager.getObjectInstance(...) " +
			"which employs certain conventions to dereference with an unspecified factoryClassName. " +
			"Reference: " + ref
		    );

		// Enforce factory-class whitelist when one has been configured.
		Set allowedFactoryClassNames = findObjectFactoryWhitelist( mcfg );
		if (allowedFactoryClassNames != null && !allowedFactoryClassNames.contains(fClassName))
		    throw new NamingException(
			"factoryClassName '" + fClassName + "' is not in the configured " +
			SecurityConfigKey.OBJECT_FACTORY_WHITELIST + " [" + allowedFactoryClassNames + "]"
		    );

		ClassLoader defaultClassLoader = Thread.currentThread().getContextClassLoader();
		if ( defaultClassLoader == null ) defaultClassLoader = ReferenceableUtils.class.getClassLoader();
		
		ClassLoader cl;
		if ( fClassLocation == null )
		    cl = defaultClassLoader;
		else
		    {
			if ( supportReferenceRemoteFactoryClassLocation( mcfg ) )
			    {
				URL u = new URL( fClassLocation );
				cl = new URLClassLoader( new URL[] { u }, defaultClassLoader );
			    }
			else
			    {
				if ( logger.isLoggable( MLevel.WARNING ) )
				    logger.log(
					MLevel.WARNING,
					"A javax.naming.Reference we have been tasked to dereference specifies a potentially remote " +
					"factory class location '" + fClassLocation + "'. " +
					"This is dangerous – a malicious reference could load and execute arbitrary code. " +
					"Remote factory class loading is disabled by default (CVE-2026-27727). " +
					"The factoryClassLocation will be ignored; dereferencing will proceed using " +
					"the calling Thread's context ClassLoader (or the ClassLoader that loaded " +
					"com.mchange.v2.naming.ReferenceableUtils). " +
					"To re-enable remote class loading set system property '" +
					SecurityConfigKey.SUPPORT_REFERENCE_REMOTE_FACTORY_CLASS_LOCATION + "=true'."
				    );
				cl = defaultClassLoader;
			    }
		    }
		
		Class fClass = Class.forName( fClassName, true, cl );
		ObjectFactory of = (ObjectFactory) fClass.newInstance();
		return of.getObjectInstance( ref, name, nameCtx, env );
	    }
	catch ( Exception e )
	    {
		if (Debug.DEBUG) 
		    {
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log( MLevel.FINE, "Could not resolve Reference to Object!", e);
		    }
		NamingException ne = new NamingException("Could not resolve Reference to Object!");
		ne.setRootCause( e );
		throw ne;
	    }
    }

    public static boolean nameLocalityIsAcceptable( Object jndiName, MultiPropertiesConfig mcfg )
    {
	boolean resolveNonlocal = permitNonlocalJndiNames( mcfg );
	if ( jndiName instanceof String )
	    return resolveNonlocal || jndiNameIsLocal((String) jndiName);
	else if ( jndiName instanceof Name )
	    return resolveNonlocal || jndiNameIsLocal((Name) jndiName);
	else
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log(
			MLevel.WARNING,
			"Putative JNDI name of unexpected type. We expect String or javax.naming.Name. " +
			"We conservatively, redundantly, disallow any attempt to lookup of jndi names of unknown types. There is no API to do so. " +
			"Putative JNDI name: " + jndiName
		    );
		return false;
	    }
    }

    public static boolean jndiNameIsLocal( String name )
    { return name.startsWith("java:"); }

    public static boolean jndiNameIsLocal( Name name )
    {
	// for now we don't know how to prove to ourselves that a javax.naming.Name is local
	// we are open to suggestions!
	return false;
    }

    public static boolean permitNonlocalJndiNames( MultiPropertiesConfig mcfg )
    { return falseBiasedLookupSyspropsPropertiesConfig( SecurityConfigKey.PERMIT_NONLOCAL_JNDI_NAMES, mcfg ); }

    private static boolean supportReferenceRemoteFactoryClassLocation( MultiPropertiesConfig mcfg )
    { return falseBiasedLookupSyspropsPropertiesConfig( SecurityConfigKey.SUPPORT_REFERENCE_REMOTE_FACTORY_CLASS_LOCATION, mcfg ); }

    private static boolean falseBiasedLookupSyspropsPropertiesConfig( String propStyleKey, MultiPropertiesConfig mcfg )
    {
	String systemPropertiesBasedShouldSupportStr = System.getProperty( propStyleKey );
	Boolean systemPropertiesBasedShouldSupport = systemPropertiesBasedShouldSupportStr == null ? null : Boolean.valueOf( systemPropertiesBasedShouldSupportStr );

	Boolean mcfgBasedShouldSupport;
	if ( mcfg != null )
	    {
		String mcfgBasedShouldSupportStr = mcfg.getProperty( propStyleKey );
		mcfgBasedShouldSupport = mcfgBasedShouldSupportStr == null ? null : Boolean.valueOf( mcfgBasedShouldSupportStr );
	    }
	else
	    mcfgBasedShouldSupport = null;

	if ( Boolean.FALSE.equals( systemPropertiesBasedShouldSupport ) )
	    {
		if ( Boolean.TRUE.equals( mcfgBasedShouldSupport ) && logger.isLoggable( MLevel.WARNING ) )
		    logger.log(
			MLevel.WARNING,
			"Security-sensitive property '" + propStyleKey +
			"' has been set to 'false' in System properties. Disabling loading of remote factory classes in System properties " +
			"OVERRIDES any configuration of this property set elsewhere, regardless of any alternative prioritization of system properties you may have configured. " +
			"Please resolve the inconsistency of configuration."
		    );
		return false;
	    }
	else if ( Boolean.TRUE.equals( systemPropertiesBasedShouldSupport ) )
	    {
		if ( Boolean.FALSE.equals( mcfgBasedShouldSupport ) )
		    {
			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.log(
				MLevel.WARNING,
				"Security-sensitive property '" + propStyleKey +
				"' has been set to 'true' in System properties, however it has been set to 'false' in other configuration supplied. Disabling loading of remote factory classes in " +
				"supplied configuration overrides permission granted in System properties. " +
				"Please resolve the inconsistency of configuration."
			    );
			return false;
		    }
		else
		    return true;
	    }
	else
	    return Boolean.TRUE.equals( mcfgBasedShouldSupport );
    }

    private static Set findObjectFactoryWhitelist( MultiPropertiesConfig mcfg )
    {
	String rawSysProp  = System.getProperty( SecurityConfigKey.OBJECT_FACTORY_WHITELIST );
	String rawMcfgProp = mcfg == null ? null : mcfg.getProperty( SecurityConfigKey.OBJECT_FACTORY_WHITELIST );

	if (rawSysProp == null && rawMcfgProp == null)
	    return null;
	else if (rawSysProp != null && rawMcfgProp == null)
	    return commaSeparatedStringListToSet( rawSysProp );
	else if (rawSysProp == null && rawMcfgProp != null)
	    return commaSeparatedStringListToSet( rawMcfgProp );
	else
	    {
		Set sysPropSet  = new HashSet( Arrays.asList( rawSysProp.split("\\s*,\\s*") ) );
		Set mcfgPropSet = new HashSet( Arrays.asList( rawMcfgProp.split("\\s*,\\s*") ) );

		if ( sysPropSet.equals( mcfgPropSet ) )
		    return Collections.unmodifiableSet( sysPropSet );
		else
		    {
			sysPropSet.retainAll( mcfgPropSet );
			Set out = Collections.unmodifiableSet( sysPropSet );
			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.log(
				MLevel.WARNING,
				"Inconsistent values of '" + SecurityConfigKey.OBJECT_FACTORY_WHITELIST +
				"' were found in System properties and the provided configuration. " +
				"Conservatively using the *intersection* of those values. " +
				"System properties value: '" + rawSysProp + "'; " +
				"Configuration value: '" + rawMcfgProp + "'; " +
				"Intersection: " + out
			    );
			return out;
		    }
	    }
    }

    private static Set commaSeparatedStringListToSet( String csList )
    {
	String[] items = csList.split("\\s*,\\s*");
	return Collections.unmodifiableSet( new HashSet( Arrays.asList( items ) ) );
    }

    
    /**
     * @deprecated nesting references seemed useful until I realized that
     *             references are Serializable and can be stored in a BinaryRefAddr.
     *             Oops.
     */
    public static void appendToReference(Reference appendTo, Reference orig)
	throws NamingException
    {
	int len = orig.size();
	appendTo.add( new StringRefAddr( REFADDR_VERSION, String.valueOf( CURRENT_REF_VERSION ) ) );
	appendTo.add( new StringRefAddr( REFADDR_CLASSNAME, orig.getClassName() ) );
	appendTo.add( new StringRefAddr( REFADDR_FACTORY, orig.getFactoryClassName() ) );
	appendTo.add( new StringRefAddr( REFADDR_FACTORY_CLASS_LOCATION, 
					 orig.getFactoryClassLocation() ) );
	appendTo.add( new StringRefAddr( REFADDR_SIZE, String.valueOf(len) ) );
	for (int i = 0; i < len; ++i)
	    appendTo.add( orig.get(i) );
    }

    /**
     * @deprecated nesting references seemed useful until I realized that
     *             references are Serializable and can be stored in a BinaryRefAddr.
     *             Oops.
     */
    public static ExtractRec extractNestedReference(Reference extractFrom, int index)
	throws NamingException
    {
	try
	    {
		int version = Integer.parseInt((String) extractFrom.get(index++).getContent());
		if (version == 1)
		    {
			String className = (String) extractFrom.get(index++).getContent();
			String factoryClassName = (String) extractFrom.get(index++).getContent();
			String factoryClassLocation = (String) extractFrom.get(index++).getContent();

			Reference outRef = new Reference( className, 
							  factoryClassName,
							  factoryClassLocation );
			int size = Integer.parseInt((String) extractFrom.get(index++).getContent());
			for (int i = 0; i < size; ++i)
			    outRef.add( extractFrom.get( index++ ) );
			return new ExtractRec( outRef, index );
		    }
		else
		    throw new NamingException("Bad version of nested reference!!!");
	    }
	catch (NumberFormatException e)
	    {
		if (Debug.DEBUG) 
		    {
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log( MLevel.FINE, "Version or size nested reference was not a number!!!", e);
		    }
		throw new NamingException("Version or size nested reference was not a number!!!"); 
	    }
    }

    /**
     * @deprecated nesting references seemed useful until I realized that
     *             references are Serializable and can be stored in a BinaryRefAddr.
     *             Oops.
     */
    public static class ExtractRec
    {
	public Reference ref;

	/**
	 *  return the first RefAddr index that the function HAS NOT read to
	 *  extract the reference.
	 */
	public int       index;

	private ExtractRec(Reference ref, int index)
	{
	    this.ref   = ref;
	    this.index = index;
	}
    }

    private ReferenceableUtils()
    {}
}
