package unreal
import unreal.JenkinsBase

def GetP4WS(Streamdir, Streamname) {
    P4WS = [$class: 'StreamWorkspaceImpl',
    charset: 'none', charset: 'none', format: new JenkinsBase().GetJobType() + '-${NODE_NAME}-' + "${Streamname}" + '-${EXECUTOR_NUMBER}-jenkins', pinHost: false, streamName: "${Streamdir}${Streamname}"]
    return P4WS
}

def P4Submit(creds, ws) {
    def p4 = p4(credential: creds, workspace : ws)
	println p4.run('info')['Client Stream']
}


return this