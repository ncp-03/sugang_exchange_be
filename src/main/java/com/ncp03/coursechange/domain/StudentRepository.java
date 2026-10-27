package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllByOrderByIdAsc();

    Optional<Student> findByStudentNo(String studentNo);
}
