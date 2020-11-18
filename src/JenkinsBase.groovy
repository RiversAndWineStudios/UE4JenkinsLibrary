package unreal;

def RunCommand(def Command)
{
	if(isUnix())
	{
		sh(script: Command, returnStdout: true)
	}
	else
	{
		bat(script: Command, returnStdout: true)
	}
}