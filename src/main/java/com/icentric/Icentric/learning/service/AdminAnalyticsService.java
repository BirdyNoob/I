package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.AdminAnalyticsResponse;
import com.icentric.Icentric.learning.repository.QuizAttemptRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public AdminAnalyticsService(
            UserRepository userRepository,
            UserAssignmentRepository assignmentRepository,
            QuizAttemptRepository quizAttemptRepository
    ) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.quizAttemptRepository = quizAttemptRepository;
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
}
