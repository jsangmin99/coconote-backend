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
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
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

    /** ==================== CHUNK - PAGING 방식 ==================== **/

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

    /** ==================== CHUNK - CURSOR 방식 ==================== **/

    @Bean
    public Job canvasCleanupChunkCursorJob() {
        return new JobBuilder("canvasCleanupChunkCursorJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(deleteCanvasesWithCursorStep())
                .build();
    }

    @Bean
    public Step deleteCanvasesWithCursorStep() {
        return new StepBuilder("deleteCanvasesWithCursorStep", jobRepository)
                .<Canvas, Canvas>chunk(10, transactionManager)
                .reader(canvasCursorReader())
                .processor(passThroughCanvasProcessor())
                .writer(deleteCanvasEntitiesWriter())
                .build();
    }

    @Bean
    public JpaCursorItemReader<Canvas> canvasCursorReader() {
        return new JpaCursorItemReaderBuilder<Canvas>()
                .name("canvasCursorReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT c FROM Canvas c WHERE c.isDeleted = 'Y' AND c.deletedTime < :oneMonthAgo")
                .parameterValues(Collections.singletonMap("oneMonthAgo", LocalDateTime.now().minusMonths(1)))
                .build();
    }

    /** ==================== TASKLET 방식 ==================== **/

    @Bean
    public Job canvasCleanupTaskletJob() {
        return new JobBuilder("canvasCleanupTaskletJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(deleteCanvasesWithTaskletStep())
                .build();
    }

    @Bean
    public Step deleteCanvasesWithTaskletStep() {
        return new StepBuilder("deleteCanvasesWithTaskletStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    EntityManager em = entityManagerFactory.createEntityManager();
                    try {
                        em.getTransaction().begin(); // 트랜잭션 시작

                        List<Long> ids = em.createQuery(
                                        "SELECT c.id FROM Canvas c WHERE c.isDeleted = 'Y' AND c.deletedTime < :oneMonthAgo", Long.class)
                                .setParameter("oneMonthAgo", LocalDateTime.now().minusMonths(1))
                                .getResultList();

                        if (!ids.isEmpty()) {
                            em.createQuery("DELETE FROM Canvas c WHERE c.parentCanvas.id IN :ids")
                                    .setParameter("ids", ids)
                                    .executeUpdate();

                            em.createQuery("DELETE FROM Canvas c WHERE c.id IN :ids")
                                    .setParameter("ids", ids)
                                    .executeUpdate();
                        }

                        em.getTransaction().commit();
                    } catch (Exception e) {
                        em.getTransaction().rollback(); // 실패 시 롤백
                        System.err.println("Error during Tasklet canvas deletion: " + e.getMessage());
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** ==================== 공통 PROCESSOR & WRITER ==================== **/

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
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                em.getTransaction().begin(); // 트랜잭션 시작
                List<Long> canvasIds = items.getItems().stream()
                        .map(Canvas::getId)
                        .collect(Collectors.toList());

                if (!canvasIds.isEmpty()) {
                    em.createQuery("DELETE FROM Canvas c WHERE c.parentCanvas.id IN :ids")
                            .setParameter("ids", canvasIds)
                            .executeUpdate();

                    em.createQuery("DELETE FROM Canvas c WHERE c.id IN :ids")
                            .setParameter("ids", canvasIds)
                            .executeUpdate();
                }
                em.getTransaction().commit(); // 트랜잭션 커밋

            } catch (Exception e) {
                em.getTransaction().rollback(); // 실패 시 롤백
                System.err.println("Error during canvas deletion: " + e.getMessage());
            }
        };
    }
}

