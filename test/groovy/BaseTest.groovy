import com.lesfurets.jenkins.unit.BasePipelineTest
import mock.CurrentBuild
import mock.CustomWARPackager
import mock.Docker
import mock.Infra
import org.junit.Before
import org.junit.Test
import org.yaml.snakeyaml.Yaml

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString

class BaseTest extends BasePipelineTest {
  Map env = [:]

  @Override
  void setUp() throws Exception {
    super.setUp()
    
    binding.setVariable('env', env)
    binding.setProperty('scm', new String())
    binding.setProperty('buildPlugin', loadScript('vars/buildPlugin.groovy'))
    binding.setProperty('customWARPackager', new CustomWARPackager())
    binding.setProperty('docker', new Docker())
    binding.setProperty('infra', new Infra())
    binding.setProperty('mvnSettingsFile', 'settings.xml')

    helper.registerAllowedMethod('archiveArtifacts', [Map.class], { true })
    helper.registerAllowedMethod('checkout', [String.class], { 'OK' })
    helper.registerAllowedMethod('checkstyle', [Map.class], { true })
    helper.registerAllowedMethod('configFile', [Map.class], { 'OK' })
    helper.registerAllowedMethod('configFileProvider', [List.class, Closure.class], { l, body -> body() })
    helper.registerAllowedMethod('deleteDir', [], { true })
    helper.registerAllowedMethod('dir', [String.class], { s -> s })
    helper.registerAllowedMethod('durabilityHint', [String.class], { s -> s })
    helper.registerAllowedMethod('echo', [String.class], { s -> s })
    helper.registerAllowedMethod('error', [String.class], { s ->
      updateBuildStatus('FAILURE')
      throw new Exception(s)
    })
    helper.registerAllowedMethod('fileExists', [String.class], { s -> s })
    helper.registerAllowedMethod('findbugs', [Map.class], { true })
    helper.registerAllowedMethod('fingerprint', [String.class], { s -> s })
    helper.registerAllowedMethod('git', [String.class], { 'OK' })
    helper.registerAllowedMethod('hasDockerLabel', [], { true })
    helper.registerAllowedMethod('isUnix', [], { true })
    helper.registerAllowedMethod('lock', [String.class, Closure.class], { s, body -> body() })
    helper.registerAllowedMethod('node', [String.class, Closure.class], { s, body -> body() })

    helper.registerAllowedMethod('parallel', [Map.class, Closure.class], { l, body -> body() })
    helper.registerAllowedMethod('pwd', [], { '/foo' })
    helper.registerAllowedMethod('pwd', [Map.class], { '/bar' })
    helper.registerAllowedMethod('readFile', [String.class], { s -> s })
    helper.registerAllowedMethod('readYaml', [Map.class], {
      Yaml yaml = new Yaml()
      return yaml.load('')
    })
    helper.registerAllowedMethod('runATH', [Map.class], { })
    helper.registerAllowedMethod('runPCT', [Map.class], { })
    helper.registerAllowedMethod('sh', [String.class], { s -> s })
    helper.registerAllowedMethod('stage', [String.class], { s -> s })
    helper.registerAllowedMethod('timeout', [String.class], { s -> s })
    helper.registerAllowedMethod('timeout', [Integer.class, Closure.class], { list, body -> body() })
    helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], { list, body -> body() })
    helper.registerAllowedMethod('withEnv', [List.class, Closure.class], { list, body -> body() })
    helper.registerAllowedMethod('writeYaml', [Map.class], { })
  }
  
  def assertMethodCallContainsPattern(String methodName, String pattern) {
    return helper.callStack.findAll { call ->
      call.methodName == methodName
    }.any { call ->
      callArgsToString(call).contains(pattern)
    }
  }

  def assertMethodCall(String methodName) {
    return helper.callStack.find { call ->
      call.methodName == methodName
    } != null
  }

  def assertMethodCallOccurrences(String methodName, int compare) {
    return helper.callStack.findAll { call ->
      call.methodName == methodName
    }.size() == compare
  }
}
