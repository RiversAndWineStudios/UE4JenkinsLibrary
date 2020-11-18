package unreal
import unreal.JenkinsBase

def GetP4WS(Streamdir, Streamname) {
    P4WS = [$class: 'StreamWorkspaceImpl',
    charset: 'none', charset: 'none', format: new JenkinsBase().GetJobType() + '-${NODE_NAME}-' + "${Streamname}" + '-${EXECUTOR_NUMBER}-jenkins', pinHost: false, streamName: "${Streamdir}${Streamname}"]
    return P4WS
}

def P4Submit(creds, ws, Paths, Message) {
    def p4 = p4(credential: creds, workspace : ws)
    def info
    for(String path : Paths) {
        info = p4.run('reconcile', '-e', '-a', '-n', path)
        for( def item:  info) {
            for ( String key: item.keySet()) {
                value = item.get(key)
                println "[" + key + ":"+ value+"]"
            }
        }
    }
    /*info = p4.run('submit','-d', Message)
    for( def item:  info) {
            for ( String key: item.keySet()) {
                value = item.get(key)
                println "[" + key + ":"+ value+"]"
            }
        }*/
}


return this