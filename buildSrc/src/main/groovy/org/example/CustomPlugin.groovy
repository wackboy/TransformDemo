package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project

class CustomPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.task("showCustomPlugin") {
            doLast {
                println("hello world plugin!")
            }
        }
    }
}
