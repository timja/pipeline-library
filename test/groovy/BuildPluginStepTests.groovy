import mock.CurrentBuild
import mock.Infra
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class BuildPluginStepTests extends BaseTest {
  static final String scriptName = 'vars/buildPlugin.groovy'

  @Override
  @Before
  void setUp() throws Exception {
    super.setUp()
    env.NODE_LABELS = 'docker'
  }

  @Test
  void test_recommendedConfigurations() throws Exception {
    def script = loadScript(scriptName)
    def configurations = script.recommendedConfigurations()
    printCallStack()
    assertFalse(configurations.isEmpty())
  }

  @Test
  void test_getConfigurations_with_implicit_and_explicit() throws Exception {
    def script = loadScript(scriptName)
    try {
      // parameters are set to random values
      script.getConfigurations(configurations: true, platforms: true)
    } catch(e) {
      //NOOP
    }
    printCallStack()
    assertTrue(assertMethodCallContainsPattern('error', 'can not be used'))
    assertJobStatusFailure()
  }

  @Test
  void test_getConfigurations_explicit_without_platform() throws Exception {
    def script = loadScript(scriptName)
    try {
      script.getConfigurations(configurations: [[ jdk: '1.8' ]])
    } catch(e) {
      //NOOP
    }
    printCallStack()
    assertTrue(assertMethodCallContainsPattern('error', 'Configuration field "platform" must be specified: [jdk:1.8]'))
    assertJobStatusFailure()
  }

  @Test
  void test_getConfigurations_explicit_without_jdk() throws Exception {
    def script = loadScript(scriptName)
    try {
      script.getConfigurations(configurations: [[ platform: 'linux' ]])
    } catch(e) {
      //NOOP
    }
    printCallStack()
    assertTrue(assertMethodCallContainsPattern('error','Configuration field "jdk" must be specified: [platform:linux]'))
    assertJobStatusFailure()
  }

  @Test
  void test_getConfigurations_without_parameters() throws Exception {
    def script = loadScript(scriptName)
    def configurations = script.getConfigurations([:])

    def expected = [['platform': 'linux', 'jdk': '8', 'jenkins': null, 'javaLevel': null],
                    ['platform': 'windows', 'jdk': '8', 'jenkins': null, 'javaLevel': null]]
    assertEquals(expected, configurations)
    printCallStack()
    assertJobStatusSuccess()
  }

  @Test
  void test_getConfigurations_implicit_with_platforms() throws Exception {
    def script = loadScript(scriptName)
    def configurations = script.getConfigurations(platforms: ['bar', 'foo'])
    println configurations
    assertEquals(configurations.size, 2)
    assertNotNull(configurations.find {it.platform.equals('bar')})
    assertNotNull(configurations.find {it.platform.equals('foo')})
    printCallStack()
    assertJobStatusSuccess()
  }

  @Test
  void test_getConfigurations_implicit_with_jenkinsVersions() throws Exception {
    def script = loadScript(scriptName)
    def configurations = script.getConfigurations(jenkinsVersions: ['1.x', '2.x'])
    assertEquals(configurations.size, 4)
    assertNotNull(configurations.find{it.jenkins.equals('1.x')})
    assertNotNull(configurations.find{it.jenkins.equals('2.x')})
    printCallStack()
    assertJobStatusSuccess()
  }

  @Test
  void test_getConfigurations_implicit_with_jdkVersions() throws Exception {
    def script = loadScript(scriptName)
    def configurations = script.getConfigurations(jdkVersions: ['1.4', '1.3'])
    assertEquals(configurations.size, 4)
    assertNotNull(configurations.find{it.jdk.equals('1.4')})
    assertNotNull(configurations.find{it.jdk.equals('1.3')})
    printCallStack()
    assertJobStatusSuccess()
  }

  @Test
  void test_hasDockerLabel() throws Exception {
    def script = loadScript(scriptName)
    def value = script.hasDockerLabel()
    printCallStack()
    assertTrue(value)
  }

  @Test
  void test_buildPlugin_with_defaults() throws Exception {
    def script = loadScript(scriptName)
    // when running without any parameters
    script.call([:])
    printCallStack()
    // then it runs in a linux node
    assertTrue(assertMethodCallContainsPattern('node', 'linux'))
    // then it runs in a windows node
    assertTrue(assertMethodCallContainsPattern('node', 'windows'))
    // then it runs the junit step by default
    assertTrue(assertMethodCall('junit'))
    // then it runs the junit step with the maven test format
    assertTrue(assertMethodCallContainsPattern('junit', '**/target/surefire-reports/**/*.xml,**/target/failsafe-reports/**/*.xml'))
    assertJobStatusSuccess()
  }

  @Test
  void test_buildPlugin_with_timeout() throws Exception {
    def script = loadScript(scriptName)
    script.call(timeout: 300)
    printCallStack()
    assertTrue(assertMethodCallContainsPattern('echo', 'lowering to 180'))
    assertJobStatusSuccess()
  }

  @Test
  void test_buildPlugin_without_tests() throws Exception {
    def script = loadScript(scriptName)
    script.call(tests: [skip: true])
    printCallStack()
    // the junit step is disabled
    assertFalse(assertMethodCall('junit'))
    assertJobStatusSuccess()
  }

  @Test
  void test_buildPlugin_with_build_error() throws Exception {
    def script = loadScript(scriptName)
    binding.setProperty('infra', new Infra(buildError: true))
    try {
      script.call([:])
    } catch (ignored) {
      // intentionally left empty
    }
    printCallStack()
    // it runs the junit step
    assertTrue(assertMethodCall('junit'))
    assertJobStatusFailure()
  }

  @Test
  void test_buildPlugin_with_defaults_with_gradle() throws Exception {
    def script = loadScript(scriptName)
    // when running in a non maven project
    helper.registerAllowedMethod('fileExists', [String.class], { s -> return !s.equals('pom.xml') })
    script.call([:])
    printCallStack()
    // then it runs the junit step with the no maven test format
    assertTrue(assertMethodCallContainsPattern('junit', '**/build/test-results/**/*.xml'))
  }

  @Test
  void test_buildPlugin_with_build_error_with_gradle() throws Exception {
    def script = loadScript(scriptName)
    binding.setProperty('infra', new Infra(buildError: true))
    // when running in a non maven project
    helper.registerAllowedMethod('fileExists', [String.class], { s -> return !s.equals('pom.xml') })
    try {
      script.call([:])
    } catch (ignored) {
      // intentionally left empty
    }
    printCallStack()
    // it runs the junit step
    assertTrue(assertMethodCall('junit'))
    assertJobStatusFailure()
  }

  @Test
  void test_buildPlugin_with_failfast_and_unstable() throws Exception {
    def script = loadScript(scriptName)
    // when running with fail fast and it's UNSTABLE
    binding.setProperty('currentBuild', new CurrentBuild('UNSTABLE'))
    try {
      script.call(failFast: true)
    } catch(e) {
      //NOOP
    }
    printCallStack()
    // then throw an error
    assertTrue(assertMethodCallContainsPattern('error', 'There were test failure'))
    assertJobStatusFailure()
  }

  @Test
  void test_buildPlugin_with_findbugs_archive() throws Exception {
    def script = loadScript(scriptName)
    script.call(findbugs: [archive: true])
    printCallStack()
    // then it runs the findbugs
    assertTrue(assertMethodCallContainsPattern('findbugs', 'pattern=**/target/findbugsXml.xml'))
  }

  @Test
  void test_buildPlugin_with_checkstyle_archive() throws Exception {
    def script = loadScript(scriptName)
    script.call(checkstyle: [archive: true])
    printCallStack()
    // then it runs the findbugs
    assertTrue(assertMethodCallContainsPattern('checkstyle', '**/target/checkstyle-result.xml'))
  }

  @Test
  void test_buildPlugin_with_configurations_and_incrementals() throws Exception {
    def script = loadScript(scriptName)
    // when running with incrementals
    helper.registerAllowedMethod('fileExists', [String.class], { s -> return s.equals('.mvn/extensions.xml') })
    helper.registerAllowedMethod('readFile', [String.class], { return 'git-changelist-maven-extension' })
    // and no jenkins version
    script.call(configurations: [['platform': 'linux', 'jdk': 8, 'jenkins': null, 'javaLevel': null]])
    printCallStack()
    // then it runs the fingerprint
    assertTrue(assertMethodCallContainsPattern('fingerprint', '**/*-rc*.*/*-rc*.*'))
  }
}
