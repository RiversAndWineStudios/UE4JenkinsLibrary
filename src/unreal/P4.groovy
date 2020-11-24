package unreal
import unreal.JenkinsBase


//Returns the workspace jenkins is using
def GetP4WS(Streamdir, Streamname) {
    P4WS = [$class: 'StreamWorkspaceImpl',
    charset: 'none', charset: 'none', format: new JenkinsBase().GetJobType() + '-${NODE_NAME}-' + "${Streamname}" + '-${EXECUTOR_NUMBER}-jenkins', pinHost: false, streamName: "${Streamdir}${Streamname}"]
    return P4WS
}

//Reconciles given paths to the default changelist and submits them afterwards
//Requires credentials, workspace, a String array of paths to check, and a message which will be used as commit
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

    //info = p4.run('submit','-d', Message)
    /*for( def item:  info) {
            for ( String key: item.keySet()) {
                value = item.get(key)
                println "[" + key + ":"+ value+"]"
            }
        }*/
}

//Uses shell to set the P4Ignore. Not sure if it helps in groovy context though.
def SetP4Ignore(creds, ws, fileToSet){
    def JB = new unreal.JenkinsBase()
    JB.RunCommand("p4 set P4IGNORE=${fileToSet}")
}

return this