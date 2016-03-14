package com.entagen.jenkins

import org.apache.http.conn.HttpHostConnectException
import org.junit.Test
import groovy.mock.interceptor.MockFor
import org.apache.http.client.HttpResponseException
import groovyx.net.http.RESTClient
import net.sf.json.JSON
import net.sf.json.JSONObject

class JenkinsApiTests extends GroovyTestCase {

	@Test public void testInvalidHostThrowsConnectionException() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://some-invalid-hostname:9090/jenkins")
		assert shouldFail(UnknownHostException) { api.getJobNames("myproj") }.contains("some-invalid-hostname")
	}

	@Test public void testCantConnectToEndpointThrowsException() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:12345/jenkins")
		assert "Connection to http://localhost:12345 refused" == shouldFail(HttpHostConnectException) { api.getJobNames("myproj") }
	}

	@Test public void test404ThrowsException() {
		MockFor mockRESTClient = new MockFor(RESTClient)
		mockRESTClient.demand.get { Map<String, ?> args ->
			def ex = new HttpResponseException(404, "Not Found")
			ex.metaClass.getResponse = {-> [status: 404] }
			throw ex
		}

		mockRESTClient.use {
			JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/goodHostAndPortBadUrl")
			assert "Unexpected failure with path http://localhost:9090/goodHostAndPortBadUrl/api/json, HTTP Status Code: 404, full map: [path:api/json]" == shouldFail() { api.getJobNames("myproj") }
		}
	}

	@Test public void testGetJobNames_matchPrefix() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")

		Map json = [
			jobs: [
				[name: "myproj-FirstJob"],
				[name: "otherproj-SecondJob"]
			]
		]
		withJsonResponse(json) {
			List<String> projectNames = api.getJobNames("myproj")
			assert projectNames == ["myproj-FirstJob"]
		}
	}

	@Test public void testGetJobNames_noPrefix() {
		JenkinsApi api = new JenkinsApi(jenkinsServerUrl: "http://localhost:9090/jenkins")

		Map json = [
			jobs: [
				[name: "myproj-FirstJob"],
				[name: "otherproj-SecondJob"]
			]
		]
		withJsonResponse(json) {
			List<String> projectNames = api.jobNames
			assert projectNames.sort() == [
				"myproj-FirstJob",
				"otherproj-SecondJob"
			]
		}
	}

	@Test public void testConfigForMissingJob_worksWithRemote() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobName -> "<name>origin/master</name>" }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert "<name>origin/new/branch</name>" == api.configForMissingJob(missingJob, [])
	}

	@Test public void testConfigForMissingJob_worksWithRemote_multipleSCMPlugin() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobName -> '''<name>origin/master</name>
			<name>origin/master</name>
			<name>origin/master</name>
			<name>origin/master</name>''' }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert '''<name>origin/new/branch</name>
			<name>origin/new/branch</name>
			<name>origin/new/branch</name>
			<name>origin/new/branch</name>''' == api.configForMissingJob(missingJob, [])
	}

	@Test public void testConfigForMissingJob_worksWithRemote_multipleSCMPluginFullConfig() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobName -> '''<hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo01.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>
      <hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo02.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>''' }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert '''<hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo01.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/new/branch</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>
      <hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo02.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/new/branch</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>''' == api.configForMissingJob(missingJob, [])
	}

	@Test public void testConfigForMissingJob_worksWithRemote_multipleSCMPluginFullConfig_notAllReposHaveBranch() {
		GitApi gitApi = new GitApi()
		gitApi.metaClass.getBranchNames{
			println "In the meta class"
			if (gitUrl.contains('repo01')){
				return ["master", "new/branch"]
			} else {
				return ["master"]
			}
		}
		JenkinsApi api = new JenkinsApi()
		api.gitApi = gitApi
		println '********* start'
		api.metaClass.getJobConfig = { String jobName -> '''<project><scm class="org.jenkinsci.plugins.multiplescms.MultiSCM" plugin="multiple-scms@0.4">
		<scms>
		<hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo01.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>
      <hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo02.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>
	</scms>
  </scm></project>''' }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert new XmlSlurper().parseText('''<project><scm class="org.jenkinsci.plugins.multiplescms.MultiSCM" plugin="multiple-scms@0.4">
		<scms>
		<hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo01.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/new/branch</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>
      <hudson.plugins.git.GitSCM plugin="git@2.3.5">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>ssh://git@myGitRepo/PROJ/repo02.git</url>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>origin/master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
      </hudson.plugins.git.GitSCM>
	</scms>
  </scm></project>''') == new XmlSlurper().parseText(api.configForMissingJob(missingJob, []).toString()).toString()
	}

	@Test public void testConfigForMissingJob_worksWithoutRemote() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobName -> "<name>master</name>" }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob: templateJob)
		assert "<name>new/branch</name>" == api.configForMissingJob(missingJob, [])
	}

	@Test public void testConfigForMissingJob_worksWithExclusions() {
		JenkinsApi api = new JenkinsApi()
		api.metaClass.getJobConfig = { String jobConfig -> "<assignedNode>master</assignedNode>" }

		TemplateJob templateJob = new TemplateJob(templateBranchName: "master")
		ConcreteJob missingJob = new ConcreteJob(branchName: "new/branch", templateJob:  templateJob)
		assert "<assignedNode>master</assignedNode>" == api.configForMissingJob(missingJob, [])
	}

	public void withJsonResponse(Map toJson, Closure closure) {
		JSON json = toJson as JSONObject
		MockFor mockRESTClient = new MockFor(RESTClient)
		mockRESTClient.demand.get { Map<String, ?> args ->
			return [data: json]
		}

		mockRESTClient.use { closure() }
	}
}

