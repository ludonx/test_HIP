node {
    def String username
    def String password

    def String utilHome

    def String orgLogin
    def String region
    def String podHostName

    def String artifactType
    def String projectName
    def String folderName
    def String location
    
    def String releaseVersion
    def String gitStore
    def String gitWorkspace
    def String gitWorkspace_list_asset
    def String gitCommitMessage

    def String exportZipFilePath 
    def String importZipFilePath
    def String operationName

    stage('Preparation') {
        utilHome = 'C:/Users/ltekam/Desktop/GIT/Tools/IICS Asset Management CLI/v2/win-x86_64'

        
        orgLogin = "dev_login"
        region = "em"
        podHostName = "dm-em.informaticacloud.com"

        artifactType = "process"
        projectName = "HIP-EUROVIA"
        folderName = "/OPTIVIA"
        location = "${projectName}${folderName}"


        releaseVersion = "v1"
        gitStore = 'git@github.com:ludonx/HIP.git'
        gitWorkspace = "C:/Users/ltekam/Desktop/GIT/HIP"
        gitWorkspace_list_asset = "${gitWorkspace}/InformaticaAssetList.txt"
        gitCommitMessage = "Merge changes to master v1"

        operationName = "OPTIVIA"
        exportZipFilePath = "exportZIP/export_${operationName}_${releaseVersion}.zip"

        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${orgLogin}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            username = env.USERNAME
            password = env.PASSWORD
        }

        git url: gitStore, credentialsId: 'ssh_ludonx', branch: 'master'
        dir ("${gitWorkspace}")
            {
                bat label: 'Get the latest code from Git', script: 'git fetch origin'
                bat label: 'Reset the local repository', script: 'git reset --hard origin/master'
            }
    }

    stage('List') {
        dir ("${utilHome}")
        {
            bat label: 'List the IICS assets based on tag filter', returnStdout: true, script: "iics.exe list --region ${region} --podHostName ${podHostName} --query type==${artifactType} --query location==${location} --outputFile ${gitWorkspace_list_asset} --username ${username} --password ${password}"
        }
    }

    stage('Export') {
        dir ("${utilHome}")
        {
            
            bat label: 'Export the IICS assets listed as part of List operation', returnStdout: true, script: "iics.exe export --region ${region} --podHostName ${podHostName} --artifactsFile ${gitWorkspace_list_asset} --zipFilePath ${exportZipFilePath} --sync true --username ${username} --password ${password}"
        }
    }

    stage('Extract') {
        dir ("${utilHome}")
        {
            bat label: 'Extract the IICS assets in gitWorkspace ', returnStdout: true, script: "iics.exe extract --zipFilePath ${exportZipFilePath} --workspaceDir ${gitWorkspace}"
        }
    }
    
    stage('VersionControl') {
        dir ("${gitWorkspace}")
            {
                bat label: 'Add to commit to local repository', script: 'git add -A'
                bat label: 'Commit changes to local repository', script: 'git commit -am "Merge changes to master"'
                bat label: 'Push changes to remote repository master branch', script: "git push ${gitStore} master:master"
                bat label: 'Add the release tag if does not exist already', script: "git ls-remote --exit-code --tags origin ${releaseVersion} || git tag -a ${releaseVersion} -m NewRelease"
                bat label: 'Push the tags to remote repository', script: "git push ${gitStore} ${releaseVersion}"
            }
    }

    stage('Preparation_Migration') {

        orgLogin = "uat_login"
        region = "em"
        podHostName = "dm-em.informaticacloud.com"

        artifactType = "process"
        projectName = "HIP-EUROVIA"
        folderName = "/OPTIVIA"
        location = "${projectName}${folderName}"


        releaseVersion = "v1"
        gitStore = 'git@github.com:ludonx/HIP.git'
        gitWorkspace = "C:/Users/ltekam/Desktop/GIT/HIP"
        gitWorkspace_list_asset = "${gitWorkspace}/InformaticaAssetList.txt"
        gitCommitMessage = "Merge changes to master v1"

        operationName = "OPTIVIA"
        importZipFilePath = "importZIP/import_${operationName}_${releaseVersion}.zip"

        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${orgLogin}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            username = env.USERNAME
            password = env.PASSWORD
        }

        git url: gitStore, credentialsId: 'ssh_ludonx', branch: 'master'
        
    }
    
    stage('VersionControl_Migration') {
        bat label: 'Checking out the Release tag assets to local repo', script: "git checkout ${releaseVersion}"
    }

    stage('Package') {
        dir ("${utilHome}")
        {
            // attention : il faut s'assurer que le fichier passé à --artifactsFile à aussi les dependences 
            // car l'import se fait asset par asset sans tenir comprte des dépendances
            bat label: 'Package the IICS assets into an import ZIP', returnStdout: true, script: "iics.exe package -z ${importZipFilePath} -w ${gitWorkspace} --artifactsFile ${gitWorkspace_list_asset}"
        }
    }

    stage('Import') {
        dir ("${utilHome}")
        {
            importZipFilePath = exportZipFilePath
            // car tous les dependances n'y sont pas, ce qui générent des erreurs si une version des dépendances n'est pas encore sur l'environnement cible ( uat qua prerelease ...)
            bat label: 'Import the IICS assets into IICS uat', returnStdout: true, script: "iics.exe import --region ${region} --podHostName ${podHostName} --zipFilePath ${importZipFilePath} --username ${username} --password ${password}"
        }
    }

    stage('Publish') {
        dir ("${utilHome}")
        {
            // s'assurer que les dependance sont bien publish aussi : ( l'ordre est important dans le fichier transmit a --artifactsFile)
            bat label: 'Publish the IICS assets', returnStdout: true, script: "iics.exe publish --region ${region} --podHostName ${podHostName} --artifactsFile ${gitWorkspace_list_asset} --username ${username} --password ${password}"
        }
    }

    
}