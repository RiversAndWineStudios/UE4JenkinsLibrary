# UE4 Jenkins Library
Jenkins Pipeline Library for UE4.
Modified for Rivers and Wine Studios.
Includes extra libraries for some base functionality regarding Jenkins, aswell as a library for P4 functions.

------

### Example Usage:

```groovy
#!/usr/bin/env groovy

def UE4 = new unreal.UE4()

def BuildConfigChoices = UE4.GetBuildConfigurationChoices()

pipeline 
{
	agent any
    
	options 
	{ skipDefaultCheckout() }
    
	parameters
	{
		choice(
			choices: BuildConfigChoices,
			description: "Build Configuration",
			name: "BuildConfig"
			)
		booleanParam(defaultValue: true, description: 'Should the project be cooked?', name: 'CookProject')
		string(defaultValue: '', description: 'Maps we want to cook', name: 'MapsToCook')
	}
    
	environment 
	{
		ProjectName		= getFolderName(this)
		WorkspaceRootDir	= env.WORKSPACE
		
		UE4 = UE4.Initialise(ProjectName, ProjectRootDir)
	}
	
	stages
	{
		stage('Generate Project Files')
		{
			steps
			{
				script
				{
					UE4.GenerateProjectFiles()
				}
			}
		}
		stage('Compile')
		{
			steps
			{
				script
				{
					UE4.CompileProject(params.BuildConfig as unreal.BuildConfiguration)
				}
			}
		}
		stage('Cook')
		{
			when
			{
				expression { params.CookProject == true }
			}
			steps
			{
				script
				{
					UE4.CookProject("WindowsNoEditor", "${params.MapsToCook}")
				}
			}
		}
		stage('Build DDC') 
		{
			steps
			{
				script
				{
					UE4.BuildDDC()
				}
			}
		}
	}
}
```
