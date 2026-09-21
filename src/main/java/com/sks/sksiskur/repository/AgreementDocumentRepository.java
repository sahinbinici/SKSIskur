package com.sks.sksiskur.repository;

import com.sks.sksiskur.domain.AgreementDocument;
import com.sks.sksiskur.domain.AgreementType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgreementDocumentRepository extends JpaRepository<AgreementDocument, AgreementType> {
}
