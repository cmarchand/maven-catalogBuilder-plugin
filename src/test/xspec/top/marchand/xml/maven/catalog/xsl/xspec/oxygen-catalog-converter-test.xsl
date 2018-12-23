<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:test="http://www.jenitennison.com/xslt/unit-test"
                xmlns:x="http://www.jenitennison.com/xslt/xspec"
                xmlns:__x="http://www.w3.org/1999/XSL/TransformAliasAlias"
                xmlns:pkg="http://expath.org/ns/pkg"
                xmlns:impl="urn:x-xspec:compile:xslt:impl"
                version="3.0"
                exclude-result-prefixes="pkg impl">
   <xsl:import href="file:/Users/cmarchand/devel/github/maven-catalogBuilder-plugin/src/main/xsl/top/marchand/xml/maven/catalog/xsl/oxygen-catalog-converter.xsl"/>
   <xsl:import href="file:/Users/cmarchand/Library/Preferences/com.oxygenxml.developer/extensions/v20.1/frameworks/https___raw.githubusercontent.com_xspec_oXygen_XML_editor_xspec_support_master_build_update_site.xml/xspec.support-1.0.1/src/compiler/generate-tests-utils.xsl"/>
   <xsl:import href="file:/Users/cmarchand/Library/Preferences/com.oxygenxml.developer/extensions/v20.1/frameworks/https___raw.githubusercontent.com_xspec_oXygen_XML_editor_xspec_support_master_build_update_site.xml/xspec.support-1.0.1/src/schematron/sch-location-compare.xsl"/>
   <xsl:namespace-alias stylesheet-prefix="__x" result-prefix="xsl"/>
   <xsl:variable name="x:stylesheet-uri"
                 as="xs:string"
                 select="'file:/Users/cmarchand/devel/github/maven-catalogBuilder-plugin/src/main/xsl/top/marchand/xml/maven/catalog/xsl/oxygen-catalog-converter.xsl'"/>
   <xsl:output name="x:report" method="xml" indent="yes"/>
   <xsl:template name="x:main">
      <xsl:message>
         <xsl:text>Testing with </xsl:text>
         <xsl:value-of select="system-property('xsl:product-name')"/>
         <xsl:text> </xsl:text>
         <xsl:value-of select="system-property('xsl:product-version')"/>
      </xsl:message>
      <xsl:result-document format="x:report">
         <xsl:processing-instruction name="xml-stylesheet">type="text/xsl" href="file:/Users/cmarchand/Library/Preferences/com.oxygenxml.developer/extensions/v20.1/frameworks/https___raw.githubusercontent.com_xspec_oXygen_XML_editor_xspec_support_master_build_update_site.xml/xspec.support-1.0.1/src/compiler/format-xspec-report.xsl"</xsl:processing-instruction>
         <x:report stylesheet="{$x:stylesheet-uri}" date="{current-dateTime()}">
            <xsl:call-template name="x:d5e2"/>
            <xsl:call-template name="x:d5e17"/>
         </x:report>
      </xsl:result-document>
   </xsl:template>
   <xsl:template name="x:d5e2">
      <xsl:message>testing equality</xsl:message>
      <x:scenario>
         <x:label>testing equality</x:label>
         <x:context>
            <root attr1="value1">
               <sub1 attr2="value2">
                  <xsl:text>
          test</xsl:text>
                  <sub2 attr3="value3">
                     <xsl:text>pouet</xsl:text>
                  </sub2>
                  <xsl:text>test2
        </xsl:text>
               </sub1>
            </root>
         </x:context>
         <xsl:variable name="x:result" as="item()*">
            <xsl:variable name="impl:context-doc" as="document-node()">
               <xsl:document>
                  <root attr1="value1">
                     <sub1 attr2="value2">
                        <xsl:text>
          test</xsl:text>
                        <sub2 attr3="value3">
                           <xsl:text>pouet</xsl:text>
                        </sub2>
                        <xsl:text>test2
        </xsl:text>
                     </sub1>
                  </root>
               </xsl:document>
            </xsl:variable>
            <xsl:variable name="impl:context" select="$impl:context-doc/node()"/>
            <xsl:apply-templates select="$impl:context"/>
         </xsl:variable>
         <xsl:call-template name="test:report-value">
            <xsl:with-param name="value" select="$x:result"/>
            <xsl:with-param name="wrapper-name" select="'x:result'"/>
            <xsl:with-param name="wrapper-ns" select="'http://www.jenitennison.com/xslt/xspec'"/>
         </xsl:call-template>
         <xsl:call-template name="x:d5e10">
            <xsl:with-param name="x:result" select="$x:result"/>
         </xsl:call-template>
      </x:scenario>
   </xsl:template>
   <xsl:template name="x:d5e10">
      <xsl:param name="x:result" required="yes"/>
      <xsl:message>Same thing</xsl:message>
      <xsl:variable name="impl:expected-doc" as="document-node()">
         <xsl:document>
            <root attr1="value1">
               <sub1 attr2="value2">
                  <xsl:text>
          test</xsl:text>
                  <sub2 attr3="value3">
                     <xsl:text>pouet</xsl:text>
                  </sub2>
                  <xsl:text>test2
        </xsl:text>
               </sub1>
            </root>
         </xsl:document>
      </xsl:variable>
      <xsl:variable name="impl:expected" select="$impl:expected-doc/node()"/>
      <xsl:variable name="impl:successful"
                    as="xs:boolean"
                    select="test:deep-equal($impl:expected, $x:result, 3)"/>
      <xsl:if test="not($impl:successful)">
         <xsl:message>      FAILED</xsl:message>
      </xsl:if>
      <x:test successful="{$impl:successful}">
         <x:label>Same thing</x:label>
         <xsl:call-template name="test:report-value">
            <xsl:with-param name="value" select="$impl:expected"/>
            <xsl:with-param name="wrapper-name" select="'x:expect'"/>
            <xsl:with-param name="wrapper-ns" select="'http://www.jenitennison.com/xslt/xspec'"/>
         </xsl:call-template>
      </x:test>
   </xsl:template>
   <xsl:template name="x:d5e17">
      <xsl:message>Changing jar attribute</xsl:message>
      <x:scenario>
         <x:label>Changing jar attribute</x:label>
         <x:context>
            <catalog xmlns="fake:namespace">
               <entry value="jar:file:/here/or/there"/>
            </catalog>
         </x:context>
         <xsl:variable name="x:result" as="item()*">
            <xsl:variable name="impl:context-doc" as="document-node()">
               <xsl:document>
                  <catalog xmlns="fake:namespace">
                     <entry value="jar:file:/here/or/there"/>
                  </catalog>
               </xsl:document>
            </xsl:variable>
            <xsl:variable name="impl:context" select="$impl:context-doc/node()"/>
            <xsl:apply-templates select="$impl:context"/>
         </xsl:variable>
         <xsl:call-template name="test:report-value">
            <xsl:with-param name="value" select="$x:result"/>
            <xsl:with-param name="wrapper-name" select="'x:result'"/>
            <xsl:with-param name="wrapper-ns" select="'http://www.jenitennison.com/xslt/xspec'"/>
         </xsl:call-template>
         <xsl:call-template name="x:d5e21">
            <xsl:with-param name="x:result" select="$x:result"/>
         </xsl:call-template>
      </x:scenario>
   </xsl:template>
   <xsl:template name="x:d5e21">
      <xsl:param name="x:result" required="yes"/>
      <xsl:message>zip</xsl:message>
      <xsl:variable name="impl:expected-doc" as="document-node()">
         <xsl:document>
            <catalog xmlns="fake:namespace">
               <entry value="zip:file:/here/or/there"/>
            </catalog>
         </xsl:document>
      </xsl:variable>
      <xsl:variable name="impl:expected" select="$impl:expected-doc/node()"/>
      <xsl:variable name="impl:successful"
                    as="xs:boolean"
                    select="test:deep-equal($impl:expected, $x:result, 3)"/>
      <xsl:if test="not($impl:successful)">
         <xsl:message>      FAILED</xsl:message>
      </xsl:if>
      <x:test successful="{$impl:successful}">
         <x:label>zip</x:label>
         <xsl:call-template name="test:report-value">
            <xsl:with-param name="value" select="$impl:expected"/>
            <xsl:with-param name="wrapper-name" select="'x:expect'"/>
            <xsl:with-param name="wrapper-ns" select="'http://www.jenitennison.com/xslt/xspec'"/>
         </xsl:call-template>
      </x:test>
   </xsl:template>
</xsl:stylesheet>
