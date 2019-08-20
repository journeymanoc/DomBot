package org.journeymanoc.obediencetrainer

import org.kohsuke.github.GitHub

class GameRepository(val github: GitHub, val repositoryName: String, val id: String, val name: String) {
    override fun toString(): String {
        return "GameRepository(repositoryName='$repositoryName', id='$id', name='$name')"
    }
}
