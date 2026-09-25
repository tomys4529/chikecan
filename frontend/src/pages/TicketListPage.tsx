import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listTickets } from '../api/tickets';
import { ApiError, isUnauthorized } from '../api/client';
import type { PageResponse, TicketResponse } from '../types/ticket';
import { LoadingIndicator } from '../components/LoadingIndicator';
import { ErrorMessage } from '../components/ErrorMessage';
import { TicketStatusBadge } from '../components/TicketStatusBadge';
import { TicketPriorityBadge } from '../components/TicketPriorityBadge';
import { TicketPagination } from '../components/TicketPagination';
import { buildPageSearchParams, parsePageParam } from '../utils/pagination';
import { formatDateTime } from '../utils/dateFormat';

const PAGE_SIZE = 20;

export function TicketListPage() {
  const { user, invalidateSession } = useAuth();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const rawPage = searchParams.get('page');
  const parsedPage = parsePageParam(rawPage);
  const requestedPage = parsedPage ?? 1;

  const [pageData, setPageData] = useState<PageResponse<TicketResponse> | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // 直前にデータを取得したページ番号。requestedPageと食い違ったら
  // (URLのpageクエリ変化・不正値からの補正直後を含む)、レンダー中に直接
  // pageData/errorMessageをリセットして再取得中の表示に戻す。
  // TicketDetailPageのselectedAssigneeId同期と同じ「propの変化に合わせて
  // レンダー中にstateを調整する」パターン(useEffect内での直接setStateを避ける)。
  const [syncedPage, setSyncedPage] = useState<number | null>(null);
  const topRef = useRef<HTMLElement | null>(null);
  const isFirstLoadRef = useRef(true);

  if (parsedPage !== null && requestedPage !== syncedPage) {
    setSyncedPage(requestedPage);
    setPageData(null);
    setErrorMessage(null);
  }

  // URLのpageクエリが数字以外・0以下・小数などの不正な値の場合、
  // 履歴を汚さずreplaceで1ページ目相当のURLへ補正する。
  useEffect(() => {
    if (parsedPage === null) {
      setSearchParams(buildPageSearchParams(1), { replace: true });
    }
  }, [parsedPage, setSearchParams]);

  useEffect(() => {
    if (parsedPage === null) {
      // 上のeffectで補正URLへ置き換わるため、このレンダーでは取得しない。
      return;
    }
    let cancelled = false;

    async function load() {
      try {
        const result = await listTickets(requestedPage - 1, PAGE_SIZE);
        if (cancelled) return;

        // 総ページ数を超えた場合(他画面での削除等で件数が減った場合を含む)は
        // 安全な最後のページへreplaceで補正する。0件の場合は1ページ目を正とする。
        const lastValidPage = result.totalPages === 0 ? 1 : result.totalPages;
        if (requestedPage > lastValidPage) {
          setSearchParams(buildPageSearchParams(lastValidPage), { replace: true });
          return;
        }

        setPageData(result);
        if (!isFirstLoadRef.current) {
          // 一部の環境(テスト用のjsdom等)ではscrollIntoView自体が未実装のことがあるため、
          // オプショナルチェイニングでメソッドの存在も確認してから呼び出す。
          topRef.current?.scrollIntoView?.({ block: 'start' });
        }
        isFirstLoadRef.current = false;
      } catch (error) {
        if (cancelled) return;
        if (isUnauthorized(error)) {
          invalidateSession();
          return;
        }
        setErrorMessage(error instanceof ApiError ? error.message : 'チケット一覧の取得に失敗しました。');
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [requestedPage, parsedPage, invalidateSession, setSearchParams]);

  function handlePageChange(page: number) {
    setSearchParams(buildPageSearchParams(page));
  }

  const tickets = pageData?.content ?? null;
  // AGENTの一覧には本人へ割り当てられたチケットだけが表示され、担当者が自分であることが
  // 明らかなため、AGENTにだけ担当者列を表示しない。USER・ADMINには表示する。
  const showAssigneeColumn = user?.role !== 'AGENT';

  return (
    <section className="page" ref={topRef}>
      <div className="page-header">
        <h1>チケット一覧</h1>
        {user?.role === 'USER' && (
          <Link to="/tickets/new" className="btn btn--primary">
            新規登録
          </Link>
        )}
      </div>

      {errorMessage && <ErrorMessage message={errorMessage} />}
      {!errorMessage && tickets === null && <LoadingIndicator />}
      {!errorMessage && tickets !== null && tickets.length === 0 && <p>チケットがありません。</p>}
      {!errorMessage && tickets !== null && tickets.length > 0 && (
        <>
          <div className="table-scroll" role="region" aria-label="チケット一覧表(横スクロール可能)" tabIndex={0}>
            <table>
              <thead>
                <tr>
                  <th>タイトル</th>
                  <th>ステータス</th>
                  <th>優先度</th>
                  {showAssigneeColumn && <th>担当者</th>}
                  <th>登録日時</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                  <tr key={ticket.id}>
                    <td>
                      <Link
                        to={`/tickets/${ticket.id}`}
                        state={{ from: `${location.pathname}${location.search}` }}
                      >
                        {ticket.title}
                      </Link>
                    </td>
                    <td>
                      <TicketStatusBadge status={ticket.status} />
                    </td>
                    <td>
                      <TicketPriorityBadge priority={ticket.priority} />
                    </td>
                    {showAssigneeColumn && (
                      <td>
                        {ticket.assigneeName ?? <span className="ticket-list__unassigned">未割り当て</span>}
                      </td>
                    )}
                    <td>{formatDateTime(ticket.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {pageData !== null && pageData.totalPages > 1 && (
            <TicketPagination
              currentPage={requestedPage}
              totalPages={pageData.totalPages}
              onPageChange={handlePageChange}
            />
          )}
        </>
      )}
    </section>
  );
}
