package unreal;
import unreal.JenkinsBase;
// ------------------------------------//
// All the helper functions used above //
// ------------------------------------//

def RemoveOldBuilds() {
    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
        bat "rd /S /Q " + getWorkSpace() + "\\Temp"
    }
}

def GenerateProjectfiles() {
    bat getEngineFolder() + '/Binaries/DotNET/UnrealBuildTool.exe -projectfiles -project=' + getUDFolder() + '/UpsideDrown.uproject -game -progress'
}

def buildEditor( String platform ) {
    stage ( 'Build Editor Win64 for ' + platform ) {
        bat getEngineFolder() + "/Binaries/DotNET/UnrealBuildTool.exe ${env.PROJECT_NAME}Editor Win64 Development " + getUDFolder() + "/${env.PROJECT_NAME}.uproject"
    }
}

def ApplyVersion() {
	env.VERSION_STRING = bat(returnStdout: true, script: '''@"%JENKINS_HOME%/scripts/apply-version.py"''' + " --update --p4 --changelist=${P4_CHANGELIST} --stream=${P4STREAMNAME} -d "+getWorkSpace()).trim()
    currentBuild.displayName = "#${BUILD_NUMBER}: v${env.VERSION_STRING}"
}

def SubmitUDBinaries() {
	println "Im a lib function"
}

def resetVersion() {
	bat '''"%JENKINS_HOME%/scripts/apply-version.py"''' + " --reset -d "+getWorkSpace()
}

def buildCookRun( String platform, String buildConfig ) {
    // Dont archive for bugfix / hotfix / etc...
    Boolean can_archive_project = ( buildConfig == 'Development'
        || buildConfig == 'Shipping' )

    Boolean Storeflag = "${StoreBuild}" == "true" ? true : false;
    // Cook if we want to archive (obviously) and always cook on Win64 to check PRs won't break
    Boolean can_cook_project = can_archive_project || ( platform == 'Win64' )
    can_archive_project = can_archive_project && Storeflag

    stage ( 'Build ' + platform ) {
        bat getUATCommonArguments( platform, buildConfig ) + getUATBuildArguments()
    }

    if ( can_cook_project ) {
        stage ( 'Cook ' + platform ) {
            // Some platforms may need specific commands to be executed before the cooker starts
            executePlatformPreCookCommands( platform )
            bat getUATCommonArguments( platform, buildConfig ) + getUATCookArguments( platform, buildConfig, can_archive_project )
            executePlatformPostCookCommands( platform )
        }
    }
}

def archiveBuild(String platform, String buildConfig) {
	echo "BuildConfig: ${buildConfig}    Platform: ${platform}"
	Boolean ShouldArchiveShipping = (buildConfig == 'All' || buildConfig == 'Shipping')
	echo "ShouldArchiveShipping: ${ShouldArchiveShipping}"
	if(ShouldArchiveShipping) {
		bat '''"%SevenZipPath%/7z.exe"'''+" a -t7z "+getOutputDirectory(platform, 'Shipping')+"/"+getArchiveName(platform, 'Shipping')+ " " +getOutputDirectory(platform, 'Shipping')+"/."
	}
	Boolean ShouldArchiveDevelopment = buildConfig == 'All' || buildConfig == 'Development'
	echo "ShouldArchiveDevelopment: ${ShouldArchiveDevelopment}"
	if(ShouldArchiveDevelopment) {
		bat '''"%SevenZipPath%/7z.exe"'''+" a -t7z "+getOutputDirectory(platform, 'Development')+"/"+getArchiveName(platform, 'Development')+ " " +getOutputDirectory(platform, 'Development')+"/."
	}
}

def ArtifactLogs() {
    bat '''"%SevenZipPath%/7z.exe"'''+" a -t7z "+getWorkSpace()+"/Temp/Logs.7z"+" " + getEngineFolder()+"/Programs/AutomationTool/Saved/."
    archiveArtifacts allowEmptyArchive: true, artifacts: 'Temp/**/*.7z', caseSensitive: false, fingerprint: true
}



def getArchiveName(String platform, String buildConfig) {
    return "${P4STREAMNAME}/${env.VERSION_STRING}-${Platform}-${buildConfig}.7z"
}

def getWorkSpace() {
    return workspace
}

//Full outputh path
def getOutputDirectory( String platform, String buildConfig ) {
    return getWorkSpace() +'/'+ getOutputDirFromProjectRoot(platform, buildConfig)
}

//relative to Project folder path, so we can use it for artifacts
def getOutputDirFromProjectRoot( String platform, String buildConfig ) {
    return 'Temp/' + buildConfig + "/"+platform
}

def getEngineFolder() {
    if ( env.NODE_NAME == 'master' ) {
        return getWorkSpace() + '/Engine'
    }
    //care!
    return getWorkSpace() + '/Engine'
}

def getUDFolder() {
    println unreal.JenkinsBase().GetJobType()
    return getWorkSpace() + '/UD'
}

def getUATCommonArguments( String platform, String buildConfig ) {
    String result = getEngineFolder() + '/Build/BatchFiles/RunUAT.bat BuildCookRun -project=' + getUDFolder() + "/${env.PROJECT_NAME}.uproject -utf8output -noP4 -platform=" + platform + ' -clientconfig=' + buildConfig
	Boolean CleanFlag = "${CLEANBUILD}" == "true" ? true : false;
    result += getUATCompileFlags(platform)

    if ( buildConfig == 'Shipping' || CleanFlag) {
        result += ' -clean'
    }

    return result
}

def getUATCompileFlags(String platform) {
    // -nocompile because we already have the automation tools
    // -nocompileeditor because we built it before
    String result = ' -nocompile -nocompileeditor -prereqs -nodebuginfo -ue4exe='
    result += getEngineFolder()
    result += "/Binaries/"+platform+"/UE4Editor-Cmd.exe"
    return result
}

def getUATBuildArguments() {
    // build only. dont cook. This is done in a separate stage
    return ' -build -skipcook'
}

def getUATCookArguments( String platform, String buildConfig, Boolean archive_project ) {
    String result = ' -allmaps -cook'

    result += getUATCookArgumentsFromClientConfig( buildConfig)
    result += getUATCookArgumentsForPlatform( platform )

    if ( archive_project ) {
        result += ' -pak -package -stage -archive -archivedirectory=' + getOutputDirectory( platform, buildConfig )
    }

    return result
}

def getUATCookArgumentsFromClientConfig( String buildConfig ) {
    // Do not cook what has already been cooked if possible
    if ( buildConfig == 'Development' ) {
        return ' -iterativecooking'
    }
    // but not in shipping; Do a full cook.
    else if ( buildConfig == 'Shipping' ) {
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

return this
