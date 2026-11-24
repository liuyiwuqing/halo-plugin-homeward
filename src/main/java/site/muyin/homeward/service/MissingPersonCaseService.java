package site.muyin.homeward.service;

import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.homeward.enums.CaseStatus;
import site.muyin.homeward.model.MissingPersonCasePublicVO;
import site.muyin.homeward.model.MissingPersonCaseVO;
import site.muyin.homeward.model.WidgetResponse;
import site.muyin.homeward.query.MissingPersonCasePublicQuery;
import site.muyin.homeward.query.MissingPersonCaseQuery;
import site.muyin.homeward.scheme.MissingPersonCase;

public interface MissingPersonCaseService {

    Mono<ListResult<MissingPersonCaseVO>> listForConsole(MissingPersonCaseQuery query);

    Mono<ListResult<MissingPersonCasePublicVO>> listForPublic(MissingPersonCasePublicQuery query);

    Mono<MissingPersonCaseVO> getForConsole(String name);

    Mono<MissingPersonCaseVO> create(MissingPersonCase item);

    Mono<MissingPersonCaseVO> update(MissingPersonCase item);

    Mono<MissingPersonCaseVO> updateStatus(String name, CaseStatus status);

    Mono<MissingPersonCaseVO> delete(String name);

    Mono<MissingPersonCasePublicVO> getPublishedBySlug(String slug);

    Mono<WidgetResponse> widget(boolean publicPage);

    Mono<Long> countByStatus(CaseStatus status);

    Mono<Long> countAll();
}
