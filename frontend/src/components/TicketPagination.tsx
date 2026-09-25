import { computePageWindow } from '../utils/pagination';
import './TicketPagination.css';

interface TicketPaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/**
 * チケット一覧下部のページ切り替えUI。総ページ数が1以下の場合は
 * 呼び出し側で表示自体を出し分ける想定のため、ここでは常に描画する。
 */
export function TicketPagination({ currentPage, totalPages, onPageChange }: TicketPaginationProps) {
  const pageWindow = computePageWindow(currentPage, totalPages);
  const isFirst = currentPage <= 1;
  const isLast = currentPage >= totalPages;

  function goTo(page: number) {
    if (page === currentPage || page < 1 || page > totalPages) {
      return;
    }
    onPageChange(page);
  }

  return (
    <nav className="ticket-pagination" aria-label="チケット一覧のページ切り替え">
      <button
        type="button"
        className="ticket-pagination__btn"
        onClick={() => goTo(1)}
        disabled={isFirst}
        aria-label="最初のページへ"
      >
        &laquo;
      </button>
      <button
        type="button"
        className="ticket-pagination__btn"
        onClick={() => goTo(currentPage - 1)}
        disabled={isFirst}
        aria-label="前のページへ"
      >
        &lsaquo;
      </button>

      {pageWindow.map((page) => (
        <button
          key={page}
          type="button"
          className={`ticket-pagination__btn${page === currentPage ? ' ticket-pagination__btn--current' : ''}`}
          onClick={() => goTo(page)}
          aria-current={page === currentPage ? 'page' : undefined}
          aria-label={`${page}ページへ`}
        >
          {page}
        </button>
      ))}

      <button
        type="button"
        className="ticket-pagination__btn"
        onClick={() => goTo(currentPage + 1)}
        disabled={isLast}
        aria-label="次のページへ"
      >
        &rsaquo;
      </button>
      <button
        type="button"
        className="ticket-pagination__btn"
        onClick={() => goTo(totalPages)}
        disabled={isLast}
        aria-label="最後のページへ"
      >
        &raquo;
      </button>
    </nav>
  );
}
