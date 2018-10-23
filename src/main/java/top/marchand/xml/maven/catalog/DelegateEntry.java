/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package top.marchand.xml.maven.catalog;

/**
 * A delegate entry, for 
 * @author cmarchand
 */
public class DelegateEntry {
    public String startString;
    public String catalog;

    public DelegateEntry() {
        super();
    }

    public String getStartString() {
        return startString;
    }

    public void setStartString(String startString) {
        this.startString = startString;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }
    
}
