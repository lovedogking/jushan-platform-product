#if 0
#pragma once
#include <VzLPRClientSDK_WhiteList.h>
#include "afxwin.h"
#include "afxcmn.h"

// DlgConfigWhiteList dialog

class DlgConfigWhiteList : public CDialog
{
	DECLARE_DYNAMIC(DlgConfigWhiteList)

public:
	DlgConfigWhiteList(VzLPRClientHandle handle, CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgConfigWhiteList();

	void iOnQueryLPRecord(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP, const VZ_LPR_WLIST_CUSTOMER *pCustomer);

// Dialog Data
	enum { IDD = IDD_DLG_SET_WLIST };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	void iQueryLPRecPage();

	void iQueryCustomerPage();

	void iCleanLPRecEdit();

	void iCleanCustomerEdit();

private:
	VzLPRClientHandle m_hLPRC;

	VZ_LPR_WLIST_VEHICLE *m_pLPRecords;
	unsigned m_uLPRecNum;

	unsigned m_uCurrLPRecPageNum;

	VZ_LPR_WLIST_CUSTOMER *m_pLPCustomers;
	unsigned m_uCustomerNum;

	unsigned m_uCurrCustomerPageNum;

	CListCtrl m_cListLPRec;
	CListCtrl m_cListCustomer;

	DECLARE_MESSAGE_MAP()
public:
	virtual BOOL OnInitDialog();
	virtual BOOL DestroyWindow();
	afx_msg void OnBnClickedBtnSaveLp();
	afx_msg void OnBnClickedBtnRefreshPageLp();
	afx_msg void OnBnClickedBtnNewLp();
	afx_msg void OnBnClickedBtnNewCustomer();
	afx_msg void OnBnClickedBtnSaveCustomer();
	afx_msg void OnBnClickedBtnRefreshPageCustomer();
	afx_msg void OnBnClickedBtnPrevPageCustomer();
	afx_msg void OnBnClickedBtnNextPageCustomer();
	afx_msg void OnBnClickedBtnDelCustomer();
	afx_msg void OnLvnItemchangedListCustomer(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnLvnItemchangedListLpRec(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedBtnSetCustId();
	afx_msg void OnBnClickedBtnPrevPageLp();
	afx_msg void OnBnClickedBtnNextPageLp();
	afx_msg void OnBnClickedBtnDelLp();
	afx_msg void OnBnClickedBtnLookupLp();
	afx_msg void OnBnClickedBtnLookupCust();
};

#endif