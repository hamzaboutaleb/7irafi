package com.herafi.rating_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ratings")
public class RatingController {

    @PostMapping
    public String create() {
        return "create rating";
    }

    @GetMapping("/job/{jobId}")
    public String getByJobId(@PathVariable String jobId) {
        return "get ratings for job: " + jobId;
    }

    @GetMapping("/user/{userId}/received")
    public String getReceivedByUserId(@PathVariable String userId) {
        return "get received ratings for user: " + userId;
    }

    @GetMapping("/user/{userId}/given")
    public String getGivenByUserId(@PathVariable String userId) {
        return "get given ratings for user: " + userId;
    }

    @GetMapping("/user/{userId}/summary")
    public String getSummary(@PathVariable String userId) {
        return "get rating summary for user: " + userId;
    }
}