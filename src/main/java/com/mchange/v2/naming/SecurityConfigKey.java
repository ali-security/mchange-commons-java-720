/*
 * Distributed as part of mchange-commons-java 0.2.15
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
 * You may choose which license to apply if you wish to redistribute
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

public class SecurityConfigKey {

    public final static String SUPPORT_REFERENCE_REMOTE_FACTORY_CLASS_LOCATION = "com.mchange.v2.naming.supportReferenceRemoteFactoryClassLocation";

    // used via ReferenceableUtils utilities in c3p0, ReferenceIndirector
    public final static String PERMIT_NONLOCAL_JNDI_NAMES = "com.mchange.v2.naming.permitNonlocalJndiNames";

    // used in ReferenceableUtils.referenceToObject when no whitelist is provided explicitly
    public final static String OBJECT_FACTORY_WHITELIST = "com.mchange.v2.naming.objectFactoryWhitelist";

    private SecurityConfigKey() {}
}
