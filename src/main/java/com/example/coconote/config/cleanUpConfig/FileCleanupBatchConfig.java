package com.example.coconote.config.cleanUpConfig;

import com.example.coconote.global.fileUpload.entity.FileEntity;
import com.example.coconote.global.fileUpload.repository.FileRepository;
import com.example.coconote.global.fileUpload.service.S3Service;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableBatchProcessing
public class FileCleanupBatchConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private FileRepository fileRepository;

    @Bean
    public Job fileCleanupJob(JobRepository jobRepository) {
        return new JobBuilder("fileCleanupJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(findAndDeleteFilesStep())
                .next(deleteOrphanS3FilesStep())
                .build();
    }

    @Bean
    public Step findAndDeleteFilesStep() {
        return new StepBuilder("findAndDeleteFilesStep", jobRepository)
                .<FileEntity, FileEntity>chunk(10, transactionManager)
                .reader(fileReader())
                .processor(fileProcessor())
                .writer(fileWriter())
                .build();
    }

    @Bean
    public JpaPagingItemReader<FileEntity> fileReader() {
        return new JpaPagingItemReaderBuilder<FileEntity>()
                .name("fileReader")
                .entityManagerFactory(entityManagerFactory)
//                .queryString("SELECT f FROM FileEntity f WHERE f.isDeleted = 'Y' AND f.deletedTime < :sevenDaysAgo")
//                .parameterValues(Collections.singletonMap("sevenDaysAgo", LocalDateTime.now().minusDays(7)))
//                한달
                .queryString("SELECT f FROM FileEntity f WHERE f.isDeleted = 'Y' AND f.deletedTime < :oneMonthsAgo")
                .parameterValues(Collections.singletonMap("oneMonthsAgo", LocalDateTime.now().minusMonths(1)))
                .build();
    }

    @Bean
    public ItemProcessor<FileEntity, FileEntity> fileProcessor() {
        return file -> {
            try {
                // S3에서 파일 삭제
                s3Service.hardDeleteFileS3(file.getId());
                return null;
            } catch (Exception e) {
                // 로그 기록 후 이 항목 건너뛰기
                System.err.println("S3에서 파일 삭제 중 오류 발생: " + e.getMessage());
                return null;
            }
        };
    }

    @Bean
    public JpaItemWriter<FileEntity> fileWriter() {
        return new JpaItemWriterBuilder<FileEntity>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(false) //persist 사용하지 않고  merge 사용
                .build();
    }

    @Bean
    public Step deleteOrphanS3FilesStep() {
        return new StepBuilder("deleteOrphanS3FilesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    try {
                        Set<String> s3Keys = s3Service.listAllObjectKeysOlderThanDays(1); // 어제 이전 업로드
                        Set<String> dbKeys = fileRepository.findAll().stream()
                                .map(file -> {
                                    String url = file.getFileUrl();
                                    if (url == null || !url.contains("/")) return url;
                                    return url.substring(url.lastIndexOf('/') + 1);
                                })
                                .collect(Collectors.toSet());

                        s3Keys.removeAll(dbKeys); // DB에 없는 것만 남김

                        for (String orphanKey : s3Keys) {
                            s3Service.deleteObjectByKey(orphanKey);
                        }
                        System.out.println("✅ 유령 파일 정리 완료: " + s3Keys.size() + "건 삭제");
                    } catch (Exception e) {
                        System.err.println("❌ 유령 파일 정리 중 오류 발생: " + e.getMessage());
                    }
                    return null;
                }, transactionManager)
                .build();
    }
}

