package unreal;
import unreal.JenkinsBase;
// ------------------------------------//
// All the helper functions used above //
// ------------------------------------//

def RemoveOldBuilds() {
    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
        bat "rd /S /Q " + workspace + "\\Temp"
    }
}

def GenerateProjectfiles() {
    bat GetEngineFolder() + '/Binaries/DotNET/UnrealBuildTool.exe -projectfiles -project=' + GetUDFolder() + '/UpsideDrown.uproject -game -progress'
}

def buildEditor( String platform ) {
    stage ( 'Build Editor Win64 for ' + platform ) {
        bat GetEngineFolder() + "/Binaries/DotNET/UnrealBuildTool.exe ${env.PROJECT_NAME}Editor Win64 Development " + GetUDFolder() + "/${env.PROJECT_NAME}.uproject"
    }
}

def ApplyVersion() {
	env.VERSION_STRING = bat(returnStdout: true, script: '''@"%JENKINS_HOME%/scripts/apply-version.py"''' + " --update --p4 --changelist=${P4_CHANGELIST} --stream=${P4STREAMNAME} -d "+workspace).trim()
    currentBuild.displayName = "#${BUILD_NUMBER}: v${env.VERSION_STRING}"
}

def resetVersion() {
	bat '''"%JENKINS_HOME%/scripts/apply-version.py"''' + " --reset -d "+workspace
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
        bat GetUATCommonArguments( platform, buildConfig ) + GetUATBuildArguments()
    }

    if ( can_cook_project ) {
        stage ( 'Cook ' + platform ) {
            // Some platforms may need specific commands to be executed before the cooker starts
            executePlatformPreCookCommands( platform )
            bat GetUATCommonArguments( platform, buildConfig ) + GetUATCookArguments( platform, buildConfig, can_archive_project )
            executePlatformPostCookCommands( platform )
        }
    }
}

def archiveBuild(String platform, String buildConfig) {
	echo "BuildConfig: ${buildConfig}    Platform: ${platform}"
	Boolean ShouldArchiveShipping = (buildConfig == 'All' || buildConfig == 'Shipping')
	echo "ShouldArchiveShipping: ${ShouldArchiveShipping}"
	if(ShouldArchiveShipping) {
		bat '''"%SevenZipPath%/7z.exe"'''+" a -t7z "+GetOutputDirectory(platform, 'Shipping')+"/"+GetArchiveName(platform, 'Shipping')+ " " +GetOutputDirectory(platform, 'Shipping')+"/."
	}
	Boolean ShouldArchiveDevelopment = buildConfig == 'All' || buildConfig == 'Development'
	echo "ShouldArchiveDevelopment: ${ShouldArchiveDevelopment}"
	if(ShouldArchiveDevelopment) {
		bat '''"%SevenZipPath%/7z.exe"'''+" a -t7z "+GetOutputDirectory(platform, 'Development')+"/"+GetArchiveName(platform, 'Development')+ " " +GetOutputDirectory(platform, 'Development')+"/."
	}
}

def ArtifactLogs() {
    bat '''"%SevenZipPath%/7z.exe"'''+" a -t7z "+workspace+"/Temp/Logs.7z"+" " + GetEngineFolder()+"/Programs/AutomationTool/Saved/."
    archiveArtifacts allowEmptyArchive: true, artifacts: 'Temp/**/*.7z', caseSensitive: false, fingerprint: true
}

def GetArchiveName(String platform, String buildConfig) {
    return "${P4STREAMNAME}/${env.VERSION_STRING}-${Platform}-${buildConfig}.7z"
}

//Full outputh path
def GetOutputDirectory( String platform, String buildConfig ) {
    return workspace +'/'+ GetOutputDirFromProjectRoot(platform, buildConfig)
}

//relative to Project folder path, so we can use it for artifacts
def GetOutputDirFromProjectRoot( String platform, String buildConfig ) {
    return 'Temp/' + buildConfig + "/"+platform
}

def GetEngineFolder() {
    return workspace + '/Engine'
}

def GetUDFolder() {
    println 'Trying to print workspace'
    println workspace
    return workspace + '/UD'
}

def GetUATCommonArguments( String platform, String buildConfig ) {
    String result = GetEngineFolder() + '/Build/BatchFiles/RunUAT.bat BuildCookRun -project=' + GetUDFolder() + "/${env.PROJECT_NAME}.uproject -utf8output -noP4 -platform=" + platform + ' -clientconfig=' + buildConfig
	Boolean CleanFlag = "${CLEANBUILD}" == "true" ? true : false;
    result += GetUATCompileFlags(platform)

    if ( buildConfig == 'Shipping' || CleanFlag) {
        result += ' -clean'
    }

    return result
}

def GetUATCompileFlags(String platform) {
    // -nocompile because we already have the automation tools
    // -nocompileeditor because we built it before
    String result = ' -nocompile -nocompileeditor -prereqs -nodebuginfo -ue4exe='
    result += GetEngineFolder()
    result += "/Binaries/"+platform+"/UE4Editor-Cmd.exe"
    return result
}

def GetUATBuildArguments() {
    // build only. dont cook. This is done in a separate stage
    return ' -build -skipcook'
}

def GetUATCookArguments( String platform, String buildConfig, Boolean archive_project ) {
    String result = ' -allmaps -cook'

    result += GetUATCookArgumentsFromClientConfig( buildConfig)
    result += GetUATCookArgumentsForPlatform( platform )

    if ( archive_project ) {
        result += ' -pak -package -stage -archive -archivedirectory=' + GetOutputDirectory( platform, buildConfig )
    }

    return result
}

def GetUATCookArgumentsFromClientConfig( String buildConfig ) {
    // Do not cook what has already been cooked if possible
    if ( buildConfig == 'Development' ) {
        return ' -iterativecooking'
    }
    // but not in shipping; Do a full cook.
    else if ( buildConfig == 'Shipping' ) {
        return ' -distribution'
    }
}

def GetUATCookArgumentsForPlatform( String platform ) {
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
