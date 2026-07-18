#pragma once


// SubDlgConfigWhiteList2 dialog
#include <VzLPRClientSDK_WhiteList.h>
#include "afxcmn.h"

class SubDlgConfigWhiteList2 : public CDialog
{
	DECLARE_DYNAMIC(SubDlgConfigWhiteList2)

public:
	SubDlgConfigWhiteList2(CWnd* pParent = NULL);   // standard constructor
	virtual ~SubDlgConfigWhiteList2();

	void SetHandle(VzLPRClientHandle handle);
// Dialog Data
	enum { IDD = IDD_DLG_SET_WLIST_2 };
	
	void iOnQueryLPRecord(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP,
										  const VZ_LPR_WLIST_CUSTOMER *pCustomer);
	void iQueryPage();

	void iGetCount();

	void Refresh();

	void GetColorValue(int iValue,char* strValue);
	void GetColorIndex(int &iValue,const char* strValue);

	void GetPlateTypeValue(int iValue,char* strValue);
	void GetPlateTypeIndex(int &iValue,const char* strValue);

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
#define MAX_ENUM_COUNT 20
	VzLPRClientHandle m_hLPRC;
	int m_uRows;
	int m_uCurPageNum;
	unsigned int m_maxRows;
	VZ_LPR_WLIST_VEHICLE *m_pLPRecords;
	VZ_LPR_WLIST_CUSTOMER *m_pLPCustomers;
	VZ_LPR_WLIST_SEARCH_WHERE m_searchWhere;
	VZ_LPR_WLIST_ENUM_VALUE m_VehicleColorValues[MAX_ENUM_COUNT];
	int m_maxVehicleColorCount;

	VZ_LPR_WLIST_ENUM_VALUE m_PlateTypeValues[MAX_ENUM_COUNT];
	int m_maxPlateTypeCount;
public:
	CListCtrl m_cListLPR;
	afx_msg void OnBnClickedBtnPrevPage();
	afx_msg void OnBnClickedBtnNextPage();
	afx_msg void OnBnClickedBtnRefreshPage();
	afx_msg void OnBnClickedBtnLookup();
	virtual BOOL OnInitDialog();
	afx_msg void OnEnChangeEditLookup();
	afx_msg void OnBnClickedCleardb();
	afx_msg void OnBnClickedDeleteCustomerAndVehicles();
};
