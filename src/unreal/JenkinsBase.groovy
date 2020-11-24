package unreal;

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

def GetPollingTriggers() {
	if(IsRecurring()) {
		return 'H H/2 * * *'
	}
	return ''
}

def GetJobType() {
	return IsRecurring() ? 'Recurring' : 'Manual'
}

def IsRecurring() {
	if("${env.RecurringJob}" != 'null') {
		return env.RecurringJob
	}
	return false
}



def TryCleanup(Boolean doIt) {
	if(env.CleanWorkspace) {
		cleanup true
		cleanWs()
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

return this