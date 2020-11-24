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
	if(GetJobType() == 'Recurring') {
		return 'H H/2 * * *'
	}
	return ''
}

def GetJobType() {
	try {
		if(RecurringJob) {
			return 'Recurring'
		}
	}
	catch ( groovy.lang.MissingPropertyException e ) {
		println("RecurringJob not defined. Will check if it has a build causer")
	}
	def isStartedByUser = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause') != null
	return isStartedByUser ? 'Manual' : 'Recurring'
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

def TryGet(var) {
	if(isNull(var)) {
		println "null!"
	}
	else {
		println "not null!"
	}
}

return this