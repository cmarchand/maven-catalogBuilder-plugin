/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oxiane.xml.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author cmarchand
 */
public class CatalogModel implements Serializable {
    
    private final List<RewriteSystemModel> entries;
    
    public CatalogModel() {
        super();
        entries = new ArrayList<>();
    }

    public List<RewriteSystemModel> getEntries() {
        return entries;
    }

    @Override
    public String toString() {
        return "CatalogModel{" + "entries=\n" + entries + '}';
    }
    
    public boolean containsUriStartPrefix(final String prefix) {
        for(RewriteSystemModel rsm:entries) {
            if(rsm.getUriStartPrefix().equals(prefix)) return true;
        }
        return false;
    }
    
    
}
