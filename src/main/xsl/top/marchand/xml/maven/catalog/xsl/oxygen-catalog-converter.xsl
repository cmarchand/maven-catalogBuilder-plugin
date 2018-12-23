<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:math="http://www.w3.org/2005/xpath-functions/math"
  xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
  exclude-result-prefixes="xs math xd"
  version="3.0">
 
  <xd:doc scope="stylesheet">
    <xd:desc>
      <xd:p>Replace URIs that starts with jar:file: with URI that starts with zip:file:</xd:p>
    </xd:desc>
  </xd:doc>
  
  <xsl:mode on-no-match="shallow-copy"/>
  
  <xd:doc>
    <xd:desc>Rewrite attribute, changing starting jar:file: by zip:file:</xd:desc>
  </xd:doc>
  <xsl:template match="@*[starts-with(., 'jar:file:')]">
    <xsl:attribute name="{name(.)}" namespace="{namespace-uri(.)}" select="replace(., '^jar:file:', 'zip:file:')"/>
  </xsl:template>
</xsl:stylesheet>