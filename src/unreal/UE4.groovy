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
def JB = new JenkinsBase()

def Initialise(String projectName, String projectRoot, String engineDir = "", String defaultArguments = "")
{
	ProjectName		= projectName
	ProjectRoot		= projectRoot
    ProjectDir      = "${ProjectRoot}/UD"
	ProjectFile     = "\"${ProjectDir}/${ProjectName}.uproject\""

    if(engineDir == "")
	{
		EngineDir	= "${ProjectDir}/Engine"
	}

	DefaultArguments = defaultArguments
	
	BatchDir = isUnix() ? "${EngineDir}/Engine/Build/BatchFiles/Linux" : "${EngineDir}/Engine/Build/BatchFiles"
	ScriptInvocationType = isUnix() ?  "sh" : "bat"
	
	UBT	= "\"${BatchDir}/Build.${ScriptInvocationType}\""

	UAT = "\"${EngineDir}/Engine/Build/BatchFiles/RunUAT.${ScriptInvocationType}\""

	UE4_CMD = "\"${EngineDir}/Engine/Binaries/Win64/UE4Editor-Cmd.exe\""
    OutputPath = "${ProjectRoot}/Temp"
}



def RemoveOldBuilds() {
    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
        bat "rd /S /Q ${OutputPath}"
    }
}

def GenerateProjectfiles() {
    JB.RunCommand("\"${BatchDir}/GenerateProjectFiles.${ScriptInvocationType}\" -projectfiles -project=${ProjectFile} -game -engine -progress ${DefaultArguments}")
}

def ApplyVersion() {
	env.VERSION_STRING = bat(returnStdout: true, script: '''@"%JENKINS_HOME%/scripts/apply-version.py"''' + " --update --p4 --changelist=${P4_CHANGELIST} --stream=${P4STREAMNAME} -d "+${ProjectRoot}).trim()
    currentBuild.displayName = "#${BUILD_NUMBER}: v${env.VERSION_STRING}"
}

def CompileProject(String buildConfig, String platform = "Win64", boolean editor = true, String additionalArguments = "")
{
	String projectTarget = "${ProjectName}"
    stage ("Build - ${buildConfig}-${platform}") {

        JB.RunCommand("${UBT} ${projectTarget} ${ProjectFile} ${platform} ${buildConfig} ${additionalArguments} ${DefaultArguments} -build -skipcook")
        if(editor && (buildConfig == 'Development' || buildConfig == 'DebugGame'))
        {
            stage("Build Editor - ${buildConfig}-${platform}") {
                projectTarget += "Editor"
                JB.RunCommand("${UBT} ${projectTarget} ${ProjectFile} ${platform} ${buildConfig} ${additionalArguments} ${DefaultArguments} -build -skipcook")
            }
        }
    }
}

def CookProject( String platform, String buildConfig, boolean archive) {
    stage ( "Cook - ${buildConfig}-${platform}") {
        // Some platforms may need specific commands to be executed before the cooker starts
        executePlatformPreCookCommands( platform )
        //BuildCookRun baseline + UAT Cook arguments
        JB.RunCommand(GetUATCommonArguments()+" "+GetUATCookArguments(platform, buildConfig))
        executePlatformPostCookCommands( platform )
    }
}

def PackageProject(String platform, String buildConfig, String stagingDir, boolean usePak = true, boolean iterative = true, String cmdlineArguments = "", String additionalArguments = "")
{
    stage( "Package - ${buildConfig}-${platform}") {
	    JB.RunCommand("${UAT} BuildCookRun -project=${ProjectFile} -platform=${platform} -skipcook -skipbuild -nocompileeditor -NoSubmit -stage -package -clientconfig=${buildConfig} -pak -archive -archivedirectory="+GetOutputDirectory(platform, buildConfig)+" -cmdline=\"${cmdlineArguments}\" " + "${additionalArguments} ${DefaultArguments}")
    }
}

def GetUATCommonArguments( String platform, String buildConfig, boolean clean ) {
    String result = "${UAT} BuildCookRun -project=${ProjectFile} -platform=${platform} -clientconfig=${clientconfig} -utf8output -noP4"
    result += GetUATCompileFlags(platform)
    if ( buildConfig == 'Shipping' || clean) {
        result += ' -clean'
    }
    return result
}

def ArchiveBuild(String platform, String buildConfig) {
		JB.RunCommand('''"%SevenZipPath%/7z.exe"'''+" a -t7z "+GetOutputDirectory(platform, buildConfig)+"/"+GetArchiveName(platform, buildConfig, env.VERSION_STRING, P4STREAMNAME)+ " " +GetOutputDirectory(platform, buildConfig)+"/.")
}

def PublishArtifacts() {
    JB.RunCommand('''"%SevenZipPath%/7z.exe"'''+" a -t7z ${ProjectRoot}/Temp/Logs.7z"+" " + GetEngineFolder()+"/Programs/AutomationTool/Saved/.")
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
