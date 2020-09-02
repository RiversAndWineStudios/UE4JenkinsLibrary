package unreal_2

// ------------------------------------//
// All the helper functions used above //
// ------------------------------------//

class UEBuild {

    def getBranchType( String branch_name ) {
        if ( branch_name =~ '.*Develop' ) {
            return 'development'
    } else if ( branch_name =~ '.*Release/.*' ) {
            return 'release'
    } else if ( branch_name =~ '.*Main' ) {
            return 'master'
        }

        return 'test'
    }

    def getBranchDeploymentEnvironment( String branch_type ) {
        if ( branch_type == 'Development' ) {
            return 'Development'
    } else if ( branch_type == 'Release' ) {
            return 'Release'
    } else if ( branch_type == 'Master' ) {
            return 'Shipping'
        }

        return 'Testing'
    }

    def getClientConfig( String environment_deployment ) {
        if ( environment_deployment == 'Shipping' ) {
            return 'Shipping'
        }

        // release and development return Development
        return 'Development'
    }
    // Manually build the editor of the game using UnrealBuiltTool
    def buildEditor( String platform ) {
        stage ( 'Build Editor Win64 for ' + platform ) {
            bat getEngineFolder() + "/Binaries/DotNET/UnrealBuildTool.exe ${env.PROJECT_NAME}Editor Win64 Development " + getUDFolder() + "/${env.PROJECT_NAME}.uproject"
        }
    }

    def buildCookRun( String platform ) {
        // Dont archive for bugfix / hotfix / etc...
        Boolean can_archive_project = ( env.DEPLOYMENT_ENVIRONMENT == 'Development'
        || env.DEPLOYMENT_ENVIRONMENT == 'Shipping' )

        // Cook if we want to archive (obviously) and always cook on Win64 to check PRs won't break
        Boolean can_cook_project = can_archive_project || ( platform == 'Win64' )

        stage ( 'Build ' + platform ) {
            bat getUATCommonArguments( platform ) + getUATBuildArguments()
        }

        if ( can_cook_project ) {
            stage ( 'Cook ' + platform ) {
                // Some platforms may need specific commands to be executed before the cooker starts
                executePlatformPreCookCommands( platform )
                bat getUATCommonArguments( platform ) + getUATCookArguments( platform, env.CLIENT_CONFIG, can_archive_project )
                executePlatformPostCookCommands( platform )
            }
        }
    }

    def getWorkSpace() {
        return "${WORKSPACE}"
    }

    def getArchiveDirectory( String client_config ) {
        return getWorkSpace() + '/Temp/' + env.client_config + "/${Platform}"
    }

    def getEngineFolder() {
        if ( env.NODE_NAME == 'master' ) {
            return getWorkSpace() + '/Engine'
        }
        //care!
        return getWorkSpace() + '/Engine'
    }

    def getUDFolder() {
        return getWorkSpace() + '/UD'
    }

    def getUATCommonArguments( String platform ) {
        String result = getEngineFolder() + '/Build/BatchFiles/RunUAT.bat BuildCookRun -project=' + getUDFolder() + "/${env.PROJECT_NAME}.uproject -utf8output -noP4 -platform=" + platform + ' -clientconfig=' + env.CLIENT_CONFIG

        result += getUATCompileFlags()

        if ( env.CLIENT_CONFIG == 'Shipping' ) {
            result += ' -clean'
        }

        return result
    }

    def getUATCompileFlags() {
        // -nocompile because we already have the automation tools
        // -nocompileeditor because we built it before
        String result = ' -nocompile -nocompileeditor -prereqs -nodebuginfo -ue4exe='
        result += getEngineFolder()
        result += "\\Binaries\\${Platform}\\UE4Editor-Cmd.exe"
        return result
    }

    def getUATBuildArguments() {
        // build only. dont cook. This is done in a separate stage
        return ' -build -skipcook'
    }

    def getUATCookArguments( String platform, String client_config, Boolean archive_project ) {
        String result = ' -allmaps -cook'

        result += getUATCookArgumentsFromClientConfig( client_config)
        result += getUATCookArgumentsForPlatform( platform )

        if ( archive_project ) {
            result += ' -pak -package -stage -archive -archivedirectory=' + getArchiveDirectory( client_config )
        }

        return result
    }

    def getUATCookArgumentsFromClientConfig( String client_config ) {
        // Do not cook what has already been cooked if possible
        if ( client_config == 'Development' ) {
            return ' -iterativecooking'
        }
    // but not in shipping; Do a full cook.
    else if ( client_config == 'Shipping' ) {
            return ' -distribution'
    }
    }

    def getUATCookArgumentsForPlatform( String platform ) {
        String result = ''

        // See https://docs.unrealengine.com/latest/INT/Engine/Basics/Projects/Packaging/
        if ( platform != 'PS4' ) {
            result += ' -compressed'
        }

        if ( params.DEPLOY_BUILD ) {
            if ( platform == 'PS4' ) {
                result += " -deploy -cmdline=\" -Messaging\" -device=PS4@192.168.XXX.XXX"
            }
        else if ( platform == 'XboxOne' ) {
                result += " -deploy -cmdline=\" -Messaging\" -device=XboxOne@192.168.YYY.YYY"
        }
        }

        return result
    }

    def executePlatformPreCookCommands( String platform ) {
        if ( platform == 'PS4' ) {
        }
    }

    def executePlatformPostCookCommands( String platform ) {
        if ( platform == 'PS4' ) {
        }
    }

    // Note you will have to add some exceptions in the Jenkins security options to allow this function to run
    def abortPreviousRunningBuilds() {
        def hi = Jenkins.instance
        def pname = env.JOB_NAME.split('/')[0]

        hi.getItem( pname ).getItem(env.JOB_BASE_NAME).getBuilds().each { build ->
            def exec = build.getExecutor()

            if ( build.number < currentBuild.number && exec != null ) {
                exec.interrupt(
        Result.ABORTED,
        new CauseOfInterruption.UserInterruption(
          "Aborted by #${currentBuild.number}"
        )
      )
                println("Aborted previous running build #${build.number}")
            }
        }
    }

}
return this
