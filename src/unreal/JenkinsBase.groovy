package unreal;

//Runs a command agnostic of current platform.
def RunCommand(def Command, boolean printStdOut = true)
{
	if(isUnix())
	{
		sh(script: Command, returnStdout: !printStdOut)
	}
	else
	{
		bat(script: Command, returnStdout: !printStdOut)
	}
}

//We define Polling triggers on the basis of recurring job or manual job currently
def GetPollingTriggers() {
	if(IsRecurring()) {
		return 'H H/2 * * *'
	}
	return ''
}

def GetJobType() {
	return IsRecurring() ? 'Recurring' : 'Manual'
}

//Checks if we defined Recurring job, and takes that. False by default
def IsRecurring() {
	if(params.RecurringJob) {
		return params.RecurringJob
	}
	return false
}

//The bool we pass in should be env.VAR, so that also an undefined value would get checked for null
def TryCleanup(Boolean doIt) {
	if(doIt) {
		cleanup true
		cleanWs()
	}
}

def Zip(String archive, String dirToArchive) {
	RunCommand('''"%SevenZipPath%/7z.exe"'''+ " a -t7z ${archive} ${dirToArchive}")
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

return this