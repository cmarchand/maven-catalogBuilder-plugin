/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.marchand.xml.maven.catalog;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author cmarchand
 */
public class CatalogTest {
    
    @Test
    public void testBuildPattern() {
        String groupId = "groupe";
        String artifactId = "artifact";
        String version = "1.0.0";
        Catalog catalog = new Catalog();
        catalog.setPatternUrl("artifactId:/");
        Assert.assertEquals("artifact:/", catalog.buildPattern(groupId, artifactId, version));
        catalog.setPatternUrl("groupId:artifactId:/");
        Assert.assertEquals("groupe:artifact:/", catalog.buildPattern(groupId, artifactId, version));
        catalog.setPatternUrl("groupId:artifactId:version:/");
        Assert.assertEquals("groupe:artifact:1.0.0:/", catalog.buildPattern(groupId, artifactId, version));
    }
    
}