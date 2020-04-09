package com.ht.project.snsproject.quartz;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

@Slf4j
@Configuration
@PropertySource("application-quartz.properties")
public class QuartzConfiguration {


  @Value("${spring.db2.datasource.url}")
  String url;

  @Value("${spring.db2.datasource.username}")
  String userName;

  @Value("${spring.db2.datasource.password}")
  String password;

  @Value("${spring.db2.datasource.driver-class-name}")
  String driverClassName;

  @Autowired
  private ApplicationContext applicationContext;

  @PostConstruct
  public void init() {
    log.info("Hello world from Spring...");
  }

  /**
   * SpringBeanJobFactory 는 인스턴스 생성 중
   * schedulerContext, jobDataMap, trigger data  항목들을
   * job Bean 에 프로퍼티들로 주입하는 기능을 지원합니다.
   *
   * @return jobFactory
   */
  @Bean
  public SpringBeanJobFactory springBeanJobFactory() {

    AutoWiringSpringBeanJobFactory jobFactory = new AutoWiringSpringBeanJobFactory();
    log.debug("Configuring Job factory");

    jobFactory.setApplicationContext(applicationContext);
    return jobFactory;
  }

  /**
   * scheduler 는 SchedulerFactory 를 사용하여 인스턴스화 할 수 있다.
   * scheduler 가 생성되면 job 및 trigger 를 등록 할 수 있다.
   * Quartz 스케줄러는 스프링의 컨테이너의 빈 LifeCycle 관리에 의해서 scheduler 관련 설정이 초기화, 시작, 종료가 된다.
   * @param trigger
   * @param job
   * @param quartzDataSource
   * @return
   */
  @Bean
  public SchedulerFactoryBean scheduler(Trigger trigger, JobDetail job,
                                        @Qualifier("db2DataSource") DataSource quartzDataSource) {

    SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
    schedulerFactory.setConfigLocation(new ClassPathResource("/application-quartz.properties"));

    log.debug("Setting the Scheduler up");
    schedulerFactory.setJobFactory(springBeanJobFactory());
    schedulerFactory.setJobDetails(job);
    schedulerFactory.setTriggers(trigger);

    // Comment the following line to use the default Quartz job store.
    schedulerFactory.setDataSource(quartzDataSource);

    return schedulerFactory;
  }

  /**
   * {@summary JobDetail}
   * Quartz 는 job class 의 실제 인스턴스를 저장하지 않는다.
   * 대신에 JobDetail 클래스를 사용하여 Job 의 인스턴스를 정의할 수 있다.
   * job class 는 실행될 job 의 타입을 알 수 있도록 반드시 JobDetail 을 받아야만 한다.
   * JobDetailFactory : 스프링의 JobDetailFactoryBean 은 JobDetail 인스턴스 구성을 위한
   * Bean-style 의 사용법을 제공한다.
   * job 이 실행될 때마다 JobDetail 의 새로운 인스턴스가 만들어진다.
   * JobDetail object 는 job 의 세부 프로퍼티들을 전달한다.
   * 실행이 완료되면 인스턴스에 대한 참조가 삭제된다.
   * @return jobDetailFactory
   */
  @Bean
  public JobDetailFactoryBean jobDetail() {

    JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
    jobDetailFactory.setJobClass(GoodBatchJob.class);
    jobDetailFactory.setName("Qrtz_Job_Detail");
    jobDetailFactory.setDescription("Invoke Good Batch Job service...");
    jobDetailFactory.setDurability(true);
    return jobDetailFactory;
  }

  /**
   * Trigger 는 job 을 예약하는 매커니즘을 의미한다.
   * 즉, job 의 실행을 '시작'하는 역할을 한다.(job 과 trigger 는 책임이 명확히 분리된다.)
   * job 이외에도 trigger 에는 scheduling 요구사항에 따라 결정될 수 있는 type 이 필요하다.
   * 1 Trigger = 1 Job : 반드시 하나의 Trigger 는 반드시 하나의 Job 을 지정할 수 있다.
   * N Trigger = 1 Job : 하나의 Job 을 여러 시간때로 실행시킬 수 있다. (ex. 매주 토요일날, 매시간마다)
   * SimpleTrigger : 특정 시간에 Job 을 수행할 때 사용되며 반복 횟수와 실행 간격등을 지정할 수 있다.
   * CronTrigger : CronTrigger 는 cron 표현식으로 Trigger 를 정의하는 방식이다. CronTrigger 는 조금 더 복잡한 시간 표현이
   *               가능하다.
   * @param job job 인스턴스의 정보를 담은 객체
   * @return trigger
   */
  @Bean
  public SimpleTriggerFactoryBean trigger(JobDetail job) {

    SimpleTriggerFactoryBean trigger = new SimpleTriggerFactoryBean();
    trigger.setJobDetail(job);

    int frequencyInSec = 1000;
    log.info("Configuring trigger to fire every {} seconds", frequencyInSec);

    trigger.setRepeatInterval(frequencyInSec * 1000);
    trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
    trigger.setName("Qrtz_Trigger");
    return trigger;
  }

  /**
   * 스케줄 정보를 저장할 DB 의 Connection 지정.
   * @return DataSource
   */
  @Bean(name = "db2DataSource")
  @QuartzDataSource
  @ConfigurationProperties(prefix = "spring.db2.datasource")
  public DataSource quartzDataSource() {

    return DataSourceBuilder.create()
            .url(url)
            .username(userName)
            .password(password)
            .type(HikariDataSource.class)
            .driverClassName(driverClassName)
            .build();
  }
}