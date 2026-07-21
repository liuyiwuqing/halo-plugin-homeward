package site.muyin.homeward.service;

import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.homeward.enums.ClueStatus;
import site.muyin.homeward.model.ClueReportVO;
import site.muyin.homeward.model.ClueStatusUpdateRequest;
import site.muyin.homeward.model.ClueSubmitRequest;
import site.muyin.homeward.model.ClueSubmitResult;
import site.muyin.homeward.query.ClueReportQuery;

public interface ClueReportService {

    Mono<ListResult<ClueReportVO>> listForConsole(ClueReportQuery query);

    Mono<ClueReportVO> getForConsole(String name);

    Mono<ClueReportVO> updateStatus(String name, ClueStatusUpdateRequest request);

    Mono<ClueReportVO> delete(String name);

    Mono<ClueSubmitResult> submit(String caseSlug, ClueSubmitRequest request, ServerRequest serverRequest);

    Mono<Long> countByStatus(ClueStatus status);
}
