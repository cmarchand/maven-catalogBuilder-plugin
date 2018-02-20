/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package top.marchand.xml.maven.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Help descriptor
 * @author cmarchand
 */
@Mojo(
        name = "help", 
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES, 
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class Help extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/top/marchand/xml/maven/catalog/help.txt")))) {
            String s = br.readLine();
            while(s!=null) {
                pw.println(s);
                s = br.readLine();
            }
        } catch(IOException ex) {
            throw new MojoFailureException("while reading help file", ex);
        }
        pw.flush();
        getLog().info(sw.getBuffer().toString());
    }

}
