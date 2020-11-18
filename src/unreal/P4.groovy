package unreal
import unreal.JenkinsBase

def GetP4WS(Streamdir, Streamname) {
    P4WS = [$class: 'StreamWorkspaceImpl',
    charset: 'none', format :charset: 'none', format: new JenkinsBase().GetJobType() + '-${NODE_NAME}-' + "${Streamname}" + '-${EXECUTOR_NUMBER}-jenkins', pinHost: false, streamName: "${Streamdir}${Streamname}"]
}


return this