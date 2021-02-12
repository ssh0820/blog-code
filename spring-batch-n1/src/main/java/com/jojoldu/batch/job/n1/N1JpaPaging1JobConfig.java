package com.jojoldu.batch.job.n1;

import com.jojoldu.batch.job.domain.Store;
import com.jojoldu.batch.job.domain.StoreHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.jojoldu.batch.job.StoreBackupBatchConfig.JOB_NAME;


@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(name = "job.name", havingValue = JOB_NAME)
public class N1JpaPaging1JobConfig {

    public static final String JOB_NAME = "n1JpaPagingJob1";

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final EntityManagerFactory entityManagerFactory;

    private int chunkSize;

    @Value("${chunkSize:10}")
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Bean(name = JOB_NAME)
    public Job job() {
        return jobBuilderFactory.get(JOB_NAME)
                .start(step())
                .build();
    }

    @Bean(name = JOB_NAME+"_step")
    @JobScope
    public Step step() {
        return stepBuilderFactory.get(JOB_NAME+"_step")
                .<Store, StoreHistory>chunk(chunkSize)
                .reader(reader(null))
                .processor(processor())
                .writer(writer())
                .build();
    }

    @Bean(name = JOB_NAME+"_reader")
    @StepScope
    public JpaPagingItemReader<Store> reader (
            @Value("#{jobParameters[address]}") String address) {

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("address", address+"%");

        return new JpaPagingItemReaderBuilder<Store>()
                .pageSize(chunkSize)
                .parameterValues(parameters)
                .queryString("SELECT distinct(s) FROM Store s FETCH JOIN s.products WHERE s.address LIKE :address order by s.id")
                .entityManagerFactory(entityManagerFactory)
                .name(JOB_NAME+"_reader")
                .transacted(false)
                .build();
    }

    public ItemProcessor<Store, StoreHistory> processor() {
        return item -> new StoreHistory(item, item.getProducts());
    }

    public JpaItemWriter<StoreHistory> writer() {
        return new JpaItemWriterBuilder<StoreHistory>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}