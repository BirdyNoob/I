package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.AdminAnalyticsResponse;
import com.icentric.Icentric.learning.dto.RiskUserResponse;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.QuizAttemptRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LessonProgressRepository progressRepository;

    public AdminAnalyticsService(
            UserRepository userRepository,
            UserAssignmentRepository assignmentRepository,
            QuizAttemptRepository quizAttemptRepository,
            LessonProgressRepository progressRepository
    ) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.progressRepository=progressRepository;
    }

    public AdminAnalyticsResponse getOverview() {

        long totalUsers = userRepository.count();

        long totalAssignments = assignmentRepository.count();

        long completedAssignments =
                assignmentRepository.countByStatus("COMPLETED");

        double completionRate =
                totalAssignments == 0 ? 0 :
                        (completedAssignments * 100.0) / totalAssignments;

        Double avgScore = quizAttemptRepository.getAverageScore();

        return new AdminAnalyticsResponse(
                totalUsers,
                totalAssignments,
                completedAssignments,
                completionRate,
                avgScore == null ? 0 : avgScore * 100
        );
    }
    public List<RiskUserResponse> getRiskUsers() {

        List<UserAssignment> assignments = assignmentRepository.findAll();

        Map<UUID, List<UserAssignment>> userAssignments =
                assignments.stream().collect(Collectors.groupingBy(UserAssignment::getUserId));

        List<RiskUserResponse> result = new ArrayList<>();

        for (var entry : userAssignments.entrySet()) {

            UUID userId = entry.getKey();
            List<UserAssignment> userAssgn = entry.getValue();

            var user = userRepository.findById(userId).orElseThrow();

            long completed =
                    progressRepository.countCompletedByUser(userId);

            long total = userAssgn.size();

            double completionPercent =
                    total == 0 ? 0 : (completed * 100.0) / total;

            Double avgScore =
                    quizAttemptRepository.getAverageScoreByUser(userId);

            double score = avgScore == null ? 0 : avgScore * 100;

            boolean overdue =
                    userAssgn.stream().anyMatch(a ->
                            a.getDueDate() != null &&
                                    a.getDueDate().isBefore(Instant.now()) &&
                                    !"COMPLETED".equals(a.getStatus())
                    );

            boolean isRisk =
                    overdue ||
                            completionPercent < 50 ||
                            score < 50;

            if (isRisk) {
                result.add(new RiskUserResponse(
                        userId,
                        user.getEmail(),
                        completionPercent,
                        score,
                        overdue
                ));
            }
        }

        return result;
    }
}
