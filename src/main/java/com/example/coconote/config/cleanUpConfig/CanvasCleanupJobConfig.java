package com.example.coconote.config.cleanUpConfig;

import com.example.coconote.api.canvas.canvas.entity.Canvas;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableBatchProcessing
public class CanvasCleanupJobConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Bean
    public Job canvasCleanupChunkPagingJob() {
        return new JobBuilder("canvasCleanupChunkPagingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(deleteCanvasesWithPagingStep())
                .build();
    }

    @Bean
    public Step deleteCanvasesWithPagingStep() {
        return new StepBuilder("deleteCanvasesWithPagingStep", jobRepository)
                .<Canvas, Canvas>chunk(10, transactionManager)
                .reader(canvasPagingReader())
                .processor(passThroughCanvasProcessor())
                .writer(deleteCanvasEntitiesWriter())
                .build();
    }

    @Bean
    public JpaPagingItemReader<Canvas> canvasPagingReader() {
        return new JpaPagingItemReaderBuilder<Canvas>()
                .name("canvasPagingReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT c FROM Canvas c WHERE c.isDeleted = 'Y' AND c.deletedTime < :oneMonthAgo")
                .parameterValues(Collections.singletonMap("oneMonthAgo", LocalDateTime.now().minusMonths(1)))
                .build();
    }

    @Bean
    public ItemProcessor<Canvas, Canvas> passThroughCanvasProcessor() {
        return canvas -> {
            try {
                return canvas;
            } catch (Exception e) {
                System.err.println("Error processing canvas deletion: " + e.getMessage());
                return null;
            }
        };
    }

    @Bean
    public ItemWriter<Canvas> deleteCanvasEntitiesWriter() {
        return items -> {
            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                entityManager.getTransaction().begin();

                List<Long> canvasIds = items.getItems().stream()
                        .map(Canvas::getId)
                        .collect(Collectors.toList());

                if (!canvasIds.isEmpty()) {
                    entityManager.createQuery("DELETE FROM Canvas c WHERE c.parentCanvas.id IN :ids")
                            .setParameter("ids", canvasIds)
                            .executeUpdate();

                    entityManager.createQuery("DELETE FROM Canvas c WHERE c.id IN :ids")
                            .setParameter("ids", canvasIds)
                            .executeUpdate();
                }

                entityManager.getTransaction().commit();
            } catch (Exception e) {
                System.err.println("Error during canvas deletion: " + e.getMessage());
            }
        };
    }
}