#!/usr/bin/groovy
package unreal;
import unreal.JenkinsBase;
// ------------------------------------//
// All the helper functions used above //
// ------------------------------------//

/* Project Specific Directories */
def EngineDir	= ''
def ProjectName = ''
def ProjectDir	= ''
def ProjectFile	= ''
def ProjectRoot = ''

/* Return BatchFiles Dir */
def BatchDir = ''
def ScriptInvocationType = ''

/* Return UBT */
def UBT	= ''

/* Return UAT */
def UAT = ''

/* Return the editor CMD */
def UE4_CMD = ''

/* Arguments to pass to all commands. e.g -BuildMachine */
def DefaultArguments = ''
def UAT_CommonArguments = ''
def OutputPath = ''

def Initialise(String projectName, String projectRoot, String engineDir = "", String defaultArguments = "")
{
	ProjectName		= projectName
	ProjectRoot		= projectRoot
    ProjectDir      = "${ProjectRoot}/UD"
	ProjectFile     = "\"${ProjectDir}/${ProjectName}.uproject\""

    if(engineDir == "")
	{
		EngineDir	= "${ProjectRoot}/Engine"
	}

	DefaultArguments = defaultArguments
	
	BatchDir = isUnix() ? "${EngineDir}/Build/BatchFiles/Linux" : "${EngineDir}/Build/BatchFiles"
	ScriptInvocationType = isUnix() ?  "sh" : "bat"
	
	UBT	= "\"${BatchDir}/Build.${ScriptInvocationType}\""

	UAT = "\"${EngineDir}/Build/BatchFiles/RunUAT.${ScriptInvocationType}\""

	UE4_CMD = "\"${EngineDir}/Binaries/Win64/UE4Editor-Cmd.exe\""
    OutputPath = "${ProjectRoot}/Temp"
}



def RemoveOldBuilds() {
    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
        if(isUnix()) {
            RunCommand("rm -rf ${OutputhPath}")
        }
        else {
            RunCommand("rd /S /Q ${OutputPath}")
        }
    }
}

def GenerateProjectfiles() {
    new JenkinsBase().RunCommand("\"${BatchDir}/GenerateProjectFiles.${ScriptInvocationType}\" -projectfiles -project=${ProjectFile} -game -engine ${DefaultArguments}")
}

def ApplyVersion() {
	env.VERSION_STRING = new JenkinsBase().RunCommand('''@"%JENKINS_HOME%/scripts/apply-version.py" ''' + " --update --p4 --changelist=${P4_CHANGELIST} --stream=${P4STREAMNAME} -d ${ProjectRoot}".trim(), false)
    currentBuild.displayName = "#${BUILD_NUMBER}: v${env.VERSION_STRING}"
}

def BuildEditor(String buildConfig, String platform = "Win64", String additionalArguments = "") {
    new JenkinsBase().RunCommand("${UBT} ${ProjectName}Editor ${ProjectFile} ${platform} ${buildConfig} -build -skipcook ${additionalArguments} ${DefaultArguments}")
}

def CompileProject(String buildConfig, String platform = "Win64", boolean clean = true, String additionalArguments = "")
{
    String cleanflag = clean || (buildConfig == 'Shipping') ? "-clean" : ""
    stage ("Build - ${buildConfig}-${platform}") {
        new JenkinsBase().RunCommand(GetUATCommonArguments(platform, buildConfig)+" -build -skipcook ${cleanflag} ${additionalArguments} ${DefaultArguments}")
    }
}

def CookProject( String platform, String buildConfig) {
    stage ( "Cook - ${buildConfig}-${platform}") {
        // Some platforms may need specific commands to be executed before the cooker starts
        executePlatformPreCookCommands( platform )
        //BuildCookRun baseline + UAT Cook arguments
        new JenkinsBase().RunCommand(GetUATCommonArguments(platform, buildConfig)+" "+GetUATCookArguments(platform, buildConfig))
        executePlatformPostCookCommands( platform )
    }
}

def PackageProject(String platform, String buildConfig, String cmdlineArguments = "", String additionalArguments = "")
{
    stage( "Package - ${buildConfig}-${platform}") {
	    new JenkinsBase().RunCommand("${UAT} BuildCookRun -project=${ProjectFile} -platform=${platform} -skipcook -skipbuild -nocompile -nocompileeditor -NoSubmit -stage -package -clientconfig=${buildConfig} -pak -archive -archivedirectory="+GetOutputDirectory(platform, buildConfig)+" -cmdline=\"${cmdlineArguments}\" " + "${additionalArguments} ${DefaultArguments}")
    }
}

def GetUATCommonArguments( String platform, String buildConfig) {
    String result = "${UAT} BuildCookRun -project=${ProjectFile} -platform=${platform} -clientconfig=${buildConfig} -utf8output -noP4"
    result += GetUATCompileFlags(platform)
    return result
}

def ArchiveBuild(String platform, String buildConfig) {
		new JenkinsBase().RunCommand('''"%SevenZipPath%/7z.exe"'''+" a -t7z "+GetOutputDirectory(platform, buildConfig)+"/"+GetArchiveName(platform, buildConfig, env.VERSION_STRING, P4STREAMNAME)+ " " +GetOutputDirectory(platform, buildConfig)+"/.")
}

def PublishArtifacts() {
    new JenkinsBase().RunCommand('''"%SevenZipPath%/7z.exe"'''+" a -t7z ${ProjectRoot}/Temp/Logs.7z"+" ${EngineDir}/Programs/AutomationTool/Saved/.")
    archiveArtifacts allowEmptyArchive: true, artifacts: 'Temp/**/*.7z', caseSensitive: false, fingerprint: true
}

def GetArchiveName(String platform, String buildConfig, String versionString, String folder = '') {
    return "${folder}/${versionString}-${platform}-${buildConfig}.7z"
}

//Full outputh path
def GetOutputDirectory( String platform, String buildConfig ) {
    return "${OutputPath}/${buildConfig}/${platform}"
}

def GetUATCompileFlags(String platform) {
    // -nocompile because we already have the automation tools
    // -nocompileeditor because we built it before
    return " -nocompile -nocompileeditor -prereqs -nodebuginfo -ue4exe=${UE4_CMD}"
}

//Cooking arguments
def GetUATCookArguments( String platform, String buildConfig) {
    String result = ' -allmaps -cook -skipbuild'
    result += GetUATCookArgumentsFromClientConfig( buildConfig)
    result += GetUATCookArgumentsForPlatform( platform )
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
