package org.cloudfoundry.identity.uaa.impl.config;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.cloudfoundry.identity.uaa.extensions.PollutionPreventionExtension;
import org.cloudfoundry.identity.uaa.extensions.SpringProfileCleanupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.StandardServletEnvironment;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.Enumeration;

import static org.cloudfoundry.identity.uaa.impl.config.YamlServletProfileInitializer.YML_ENV_VAR_NAME;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(PollutionPreventionExtension.class)
@ExtendWith(SpringProfileCleanupExtension.class)
@ExtendWith(LoggerContextCleanupExtension.class)
class YamlServletProfileInitializerTest {

    private YamlServletProfileInitializer initializer;
    private ConfigurableWebApplicationContext context;
    private StandardServletEnvironment environment;
    private ServletContext servletContext;

    @BeforeEach
    void setup() {
        initializer = new YamlServletProfileInitializer();
        context = mock(ConfigurableWebApplicationContext.class);
        environment = new StandardServletEnvironment();
        servletContext = mock(ServletContext.class);

        when(servletContext.getInitParameterNames()).thenReturn(new EmptyEnumerationOfString());

        when(context.getServletContext()).thenReturn(servletContext);
        when(context.getEnvironment()).thenReturn(environment);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            System.err.println(invocation.getArguments()[0]);
            return null;
        }).when(servletContext).log(ArgumentMatchers.anyString());
        when(servletContext.getContextPath()).thenReturn("/context");
    }

    @AfterEach
    void cleanup() {
        System.clearProperty("CLOUDFOUNDRY_CONFIG_PATH");
    }

    @Test
    void loadDefaultResource() {
        when(context.getResource(ArgumentMatchers.contains("${CLOUDFOUNDRY_CONFIG_PATH}"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getProperty("foo"));
        assertEquals("baz", environment.getProperty("spam.foo"));
    }

    @Test
    void loadSessionEventPublisher() {
        when(context.getResource(ArgumentMatchers.contains("${CLOUDFOUNDRY_CONFIG_PATH}"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz".getBytes()));

        initializer.initialize(context);

        ArgumentCaptor<HttpSessionEventPublisher> httpSessionEventPublisherArgumentCaptor = ArgumentCaptor.forClass(HttpSessionEventPublisher.class);
        verify(servletContext, atLeastOnce()).addListener(httpSessionEventPublisherArgumentCaptor.capture());
        assertNotNull(httpSessionEventPublisherArgumentCaptor.getValue());
    }

    @Test
    void activeProfiles() {
        System.setProperty("spring.profiles.active", "foo");

        when(context.getResource(ArgumentMatchers.anyString())).thenReturn(
                new ByteArrayResource("spring_profiles: bar".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getActiveProfiles()[0]);
    }

    @Test
    void activeProfilesFromYaml() {
        when(context.getResource(ArgumentMatchers.anyString())).thenReturn(
                new ByteArrayResource("spring_profiles: bar".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getActiveProfiles()[0]);
    }

    @Test
    void log4jConfigurationFromYaml() {
        when(context.getResource(ArgumentMatchers.anyString())).thenReturn(
                new ByteArrayResource("logging:\n  config: bar".getBytes()));
        initializer.initialize(context);
    }

    @Test
    void loadServletConfiguredFilename() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "/config/path");
        when(context.getResource(ArgumentMatchers.eq("file:/config/path/uaa.yml"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getProperty("foo"));
        assertEquals("baz", environment.getProperty("spam.foo"));
    }

    @Test
    void loadServletConfiguredResource() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "anywhere");
        when(context.getResource(ArgumentMatchers.eq("file:anywhere/uaa.yml"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz-from-config".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getProperty("foo"));
        assertEquals("baz-from-config", environment.getProperty("spam.foo"));
    }

    @Test
    void loadContextConfiguredResource() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "foo/bar");
        when(context.getResource(ArgumentMatchers.eq("file:foo/bar/uaa.yml"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz-from-context".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getProperty("foo"));
        assertEquals("baz-from-context", environment.getProperty("spam.foo"));
    }

    @Test
    void loadReplacedResource() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "foo");

        when(context.getResource(ArgumentMatchers.eq("file:foo/uaa.yml"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getProperty("foo"));
        assertEquals("baz", environment.getProperty("spam.foo"));
    }

    @Test
    void loadReplacedResourceFromFileLocation() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "bar");

        when(context.getResource(ArgumentMatchers.eq("file:bar/uaa.yml"))).thenReturn(
                new ByteArrayResource("foo: bar\nspam:\n  foo: baz".getBytes()));

        initializer.initialize(context);

        assertEquals("bar", environment.getProperty("foo"));
        assertEquals("baz", environment.getProperty("spam.foo"));
    }

    @Test
    void loggingConfigVariableWorks() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "somewhere");
        when(context.getResource(ArgumentMatchers.eq("file:somewhere/uaa.yml"))).thenReturn(
                new ByteArrayResource("logging:\n  config: /some/path".getBytes()));
        initializer.initialize(context);
        assertEquals("/some/path", environment.getProperty("logging.config"));
        assertNull(environment.getProperty("smtp.host"));
        assertNull(environment.getProperty("smtp.port"));
    }

    @Test
    void smtpFromUaaYmlWorks() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "somewhere");
        when(context.getResource(ArgumentMatchers.eq("file:somewhere/uaa.yml"))).thenReturn(
                new ByteArrayResource("smtp:\n  user: marissa\n  password: koala".getBytes()));
        initializer.initialize(context);
        assertEquals("marissa", environment.getProperty("smtp.user"));
        assertEquals("koala", environment.getProperty("smtp.password"));
    }

    @Test
    void smtpFromSecretsDirWorks() {
        System.setProperty("SECRETS_DIR", "elsewhere");
        when(context.getResource(ArgumentMatchers.eq("file:elsewhere/smtp_credentials.yml"))).thenReturn(
                new ByteArrayResource("smtp:\n  user: donkey\n  password: kong".getBytes()));
        initializer.initialize(context);
        assertEquals("donkey", environment.getProperty("smtp.user"));
        assertEquals("kong", environment.getProperty("smtp.password"));
    }

    @Test
    void smtpFromSecretsDirOverwrites() {
        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "somewhere");
        when(context.getResource(ArgumentMatchers.eq("file:somewhere/uaa.yml"))).thenReturn(
                new ByteArrayResource("smtp:\n  user: marissa\n  password: koala".getBytes()));

        System.setProperty("SECRETS_DIR", "elsewhere");
        when(context.getResource(ArgumentMatchers.eq("file:elsewhere/smtp_credentials.yml"))).thenReturn(
                new ByteArrayResource("smtp:\n  user: donkey\n  password: kong".getBytes()));
        initializer.initialize(context);

        assertEquals("donkey", environment.getProperty("smtp.user"));
        assertEquals("kong", environment.getProperty("smtp.password"));
    }

    @Test
    void readingYamlFromEnvironment() {
        SystemEnvironmentAccessor env = new SystemEnvironmentAccessor() {
            @Override
            public String getEnvironmentVariable(String name) {
                return name.equals(YML_ENV_VAR_NAME) ?
                        "uaa.url: http://uaa.test.url/\n" +
                                "login.url: http://login.test.url/\n" +
                                "smtp:\n" +
                                "  host: mail.server.host\n" +
                                "  port: 3535\n" :
                        null;
            }
        };
        initializer.setEnvironmentAccessor(env);
        initializer.initialize(context);
        assertEquals("mail.server.host", environment.getProperty("smtp.host"));
        assertEquals("3535", environment.getProperty("smtp.port"));
        assertEquals("http://uaa.test.url/", environment.getProperty("uaa.url"));
        assertEquals("http://login.test.url/", environment.getProperty("login.url"));
    }

    @Nested
    class WithFakeStdOut {

        private PrintStream originalOut;
        private PrintStream mockPrintStream;

        @BeforeEach
        void setUp() {
            originalOut = System.out;
            mockPrintStream = mock(PrintStream.class);
            System.setOut(mockPrintStream);
        }

        @AfterEach
        void tearDown() {
            System.setOut(originalOut);
        }

        @Test
        void ignoreDashDTomcatLoggingConfigVariable() {
            final String tomcatLogConfig = "-Djava.util.logging.config=/some/path/logging.properties";
            System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "foo");
            when(context.getResource(ArgumentMatchers.eq("file:foo/uaa.yml")))
                    .thenReturn(new ByteArrayResource(("logging:\n  config: " + tomcatLogConfig).getBytes()));
            environment.getPropertySources().addFirst(new PropertySource<Object>(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME) {
                @Override
                public boolean containsProperty(String name) {
                    if ("LOGGING_CONFIG".equals(name)) {
                        return true;
                    } else {
                        return super.containsProperty(name);
                    }
                }

                @Override
                public Object getProperty(String name) {
                    if ("LOGGING_CONFIG".equals(name)) {
                        return tomcatLogConfig;
                    } else {
                        return System.getenv(name);
                    }

                }
            });
            initializer.initialize(context);
            assertEquals("-Djava.util.logging.config=/some/path/logging.properties", environment.getProperty("logging.config"));

            verify(mockPrintStream, description("Expected to find a log entry indicating that the LOGGING_CONFIG variable was found."))
                    .println("Ignoring Log Config Location: -Djava.util.logging.config=/some/path/logging.properties. Location is suspect to be a Tomcat startup script environment variable");
        }
    }

    private static class EmptyEnumerationOfString implements Enumeration<String> {
        @Override
        public boolean hasMoreElements() {
            return false;
        }

        @Override
        public String nextElement() {
            return null;
        }
    }

    @ExtendWith(PollutionPreventionExtension.class)
    @ExtendWith(SpringProfileCleanupExtension.class)
    @Nested
    class ApplySpringProfiles {

        private MockEnvironment environment;
        private MockServletContext context;

        @BeforeEach
        void setup() {
            initializer = new YamlServletProfileInitializer();
            environment = new MockEnvironment();
            context = new MockServletContext();
        }

        @Test
        void tokenizeToStringArray_RemovesSpaces() {
            String profileString = "    database    ,  ldap ";
            String[] profiles = StringUtils.tokenizeToStringArray(profileString, ",", true, true);
            assertThat(profiles.length, is(2));
            assertThat(profiles[0], is("database"));
            assertThat(profiles[1], is("ldap"));
            // And show what's wrong with commaDelimitedListToStringArray
            profiles = StringUtils.commaDelimitedListToStringArray(profileString);
            assertThat(profiles.length, is(2));
            assertThat(profiles[0], is("    database    "));
            assertThat(profiles[1], is("  ldap "));
        }

        @Test
        void ifNoProfilesAreSetUseHsqldb() {
            System.clearProperty("spring.profiles.active");
            initializer.applySpringProfiles(environment);
            assertArrayEquals(new String[]{"hsqldb"}, environment.getActiveProfiles());
        }

        @Test
        void ifProfilesAreSetUseThem() {
            System.setProperty("spring.profiles.active", "hsqldb,default");
            initializer.applySpringProfiles(environment);
            assertArrayEquals(new String[]{"hsqldb", "default"}, environment.getActiveProfiles());
        }

        @Test
        void defaultProfileUnset() {
            System.setProperty("spring.profiles.active", "hsqldb");
            initializer.applySpringProfiles(environment);
            assertArrayEquals(new String[]{"hsqldb"}, environment.getActiveProfiles());
            assertArrayEquals(new String[0], environment.getDefaultProfiles());
        }

        @Test
        void yamlConfiguredProfilesAreUsed() {
            System.setProperty("spring.profiles.active", "hsqldb,default");
            environment.setProperty("spring_profiles", "mysql,default");
            initializer.applySpringProfiles(environment);
            assertArrayEquals(new String[]{"mysql", "default"}, environment.getActiveProfiles());
        }
    }

    @Test
    void appliesCustomClassPathLogProperties() throws Exception {
        File tempFile = File.createTempFile("prefix", "suffix.properties");
        File validLog4j2PropertyFile = new ClassPathResource("log4j2-test.properties").getFile();

        FileUtils.copyFile(validLog4j2PropertyFile, tempFile);

        System.setProperty("CLOUDFOUNDRY_CONFIG_PATH", "anything");
        when(context.getResource("file:anything/uaa.yml"))
                .thenReturn(new ByteArrayResource(("logging:\n  config: " + tempFile.getAbsolutePath()).getBytes()));

        initializer.initialize(context);

        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);

        URI expectedUrl = ResourceUtils.toURI("file:" + tempFile.getAbsolutePath());

        assertThat(loggerContext.getConfigLocation(), is(expectedUrl));

        tempFile.delete();
    }
}
