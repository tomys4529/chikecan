import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TicketPagination } from './TicketPagination';

describe('TicketPagination', () => {
  it('現在ページにaria-current="page"が設定される', () => {
    render(<TicketPagination currentPage={2} totalPages={5} onPageChange={vi.fn()} />);

    const current = screen.getByRole('button', { name: '2ページへ' });
    expect(current).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '1ページへ' })).not.toHaveAttribute('aria-current');
  });

  it('最初のページでは最初へ・前へボタンがdisabledになる', () => {
    render(<TicketPagination currentPage={1} totalPages={5} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '最初のページへ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '前のページへ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '次のページへ' })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '最後のページへ' })).not.toBeDisabled();
  });

  it('最後のページでは次へ・最後へボタンがdisabledになる', () => {
    render(<TicketPagination currentPage={5} totalPages={5} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '次のページへ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '最後のページへ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '最初のページへ' })).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '前のページへ' })).not.toBeDisabled();
  });

  it('数字ボタンをクリックするとそのページ番号でonPageChangeが呼ばれる', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<TicketPagination currentPage={1} totalPages={5} onPageChange={onPageChange} />);

    await user.click(screen.getByRole('button', { name: '3ページへ' }));

    expect(onPageChange).toHaveBeenCalledWith(3);
  });

  it('前へ・次へボタンは現在ページの前後1ページを指定してonPageChangeを呼ぶ', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<TicketPagination currentPage={3} totalPages={5} onPageChange={onPageChange} />);

    await user.click(screen.getByRole('button', { name: '前のページへ' }));
    await user.click(screen.getByRole('button', { name: '次のページへ' }));

    expect(onPageChange).toHaveBeenNthCalledWith(1, 2);
    expect(onPageChange).toHaveBeenNthCalledWith(2, 4);
  });

  it('最初へ・最後へボタンはそれぞれ1ページ目と最終ページを指定する', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<TicketPagination currentPage={3} totalPages={10} onPageChange={onPageChange} />);

    await user.click(screen.getByRole('button', { name: '最初のページへ' }));
    await user.click(screen.getByRole('button', { name: '最後のページへ' }));

    expect(onPageChange).toHaveBeenNthCalledWith(1, 1);
    expect(onPageChange).toHaveBeenNthCalledWith(2, 10);
  });

  it('現在ページの数字ボタンをクリックしてもonPageChangeは呼ばれない', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<TicketPagination currentPage={2} totalPages={5} onPageChange={onPageChange} />);

    await user.click(screen.getByRole('button', { name: '2ページへ' }));

    expect(onPageChange).not.toHaveBeenCalled();
  });

  it('総ページ数が多い場合は現在ページ付近の最大5個だけ数字ボタンを表示する', () => {
    render(<TicketPagination currentPage={10} totalPages={20} onPageChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '8ページへ' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '12ページへ' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '1ページへ' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '20ページへ' })).not.toBeInTheDocument();
  });

  it('nav要素にページ切り替え用のaria-labelが設定される', () => {
    render(<TicketPagination currentPage={1} totalPages={5} onPageChange={vi.fn()} />);

    expect(screen.getByRole('navigation', { name: 'チケット一覧のページ切り替え' })).toBeInTheDocument();
  });
});
