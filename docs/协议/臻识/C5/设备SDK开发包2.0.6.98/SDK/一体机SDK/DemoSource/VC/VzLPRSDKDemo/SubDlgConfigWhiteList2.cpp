// SubDlgConfigWhiteList2.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "SubDlgConfigWhiteList2.h"


// SubDlgConfigWhiteList2 dialog

static void InitVehicle(VZ_LPR_WLIST_VEHICLE* pSrc){
	memset(pSrc,0,sizeof(VZ_LPR_WLIST_VEHICLE));
	pSrc->uCustomerID = -1;
	pSrc->uVehicleID = -1;
}

static void CopyVehicle(const VZ_LPR_WLIST_VEHICLE* pSrc,VZ_LPR_WLIST_VEHICLE* pDest){	
	memcpy(pDest, pSrc, sizeof(VZ_LPR_WLIST_VEHICLE));
}

const unsigned g_PageSize = 15;

IMPLEMENT_DYNAMIC(SubDlgConfigWhiteList2, CDialog)

SubDlgConfigWhiteList2::SubDlgConfigWhiteList2(CWnd* pParent /*=NULL*/)
	: CDialog(SubDlgConfigWhiteList2::IDD, pParent)
	, m_pLPRecords(new VZ_LPR_WLIST_VEHICLE[g_PageSize]), m_uRows(0)
	, m_uCurPageNum(0)
	, m_pLPCustomers(new VZ_LPR_WLIST_CUSTOMER[g_PageSize])
{
	for(int i=0;i<g_PageSize;i++){
		InitVehicle(&m_pLPRecords[i]);
	}
	memset(m_pLPCustomers,0,sizeof(VZ_LPR_WLIST_CUSTOMER)*g_PageSize);

	m_searchWhere.pSearchConstraints = NULL;
	m_searchWhere.searchConstraintCount = 0;
	m_searchWhere.searchType = VZ_LPR_WLIST_SEARCH_TYPE_LIKE;
}

SubDlgConfigWhiteList2::~SubDlgConfigWhiteList2()
{
	delete[] m_pLPRecords;
	delete[] m_pLPCustomers;
}

void SubDlgConfigWhiteList2::SetHandle(VzLPRClientHandle handle){
	m_hLPRC = handle;
	m_maxVehicleColorCount = MAX_ENUM_COUNT;
	VzLPRClient_WhiteListGetEnumStrings(m_hLPRC,VZLPRC_WLIST_COL_VEHICLE_COLOR,m_VehicleColorValues,&m_maxVehicleColorCount);
	m_maxPlateTypeCount = MAX_ENUM_COUNT;
	VzLPRClient_WhiteListGetEnumStrings(m_hLPRC,VZLPRC_WLIST_COL_VEHICLE_PLATETYPE,m_PlateTypeValues,&m_maxPlateTypeCount);
}

void SubDlgConfigWhiteList2::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_LP, m_cListLPR);
}


BEGIN_MESSAGE_MAP(SubDlgConfigWhiteList2, CDialog)
	ON_BN_CLICKED(IDC_BTN_PREV_PAGE, &SubDlgConfigWhiteList2::OnBnClickedBtnPrevPage)
	ON_BN_CLICKED(IDC_BTN_NEXT_PAGE, &SubDlgConfigWhiteList2::OnBnClickedBtnNextPage)
	ON_BN_CLICKED(IDC_BTN_REFRESH_PAGE, &SubDlgConfigWhiteList2::OnBnClickedBtnRefreshPage)
	ON_BN_CLICKED(IDC_BTN_LOOKUP, &SubDlgConfigWhiteList2::OnBnClickedBtnLookup)
	ON_EN_CHANGE(IDC_EDIT_LOOKUP, &SubDlgConfigWhiteList2::OnEnChangeEditLookup)
	ON_BN_CLICKED(IDC_CLEARDB, &SubDlgConfigWhiteList2::OnBnClickedCleardb)
	ON_BN_CLICKED(IDC_DELETE_CUSTOMER_AND_VEHICLES, &SubDlgConfigWhiteList2::OnBnClickedDeleteCustomerAndVehicles)
END_MESSAGE_MAP()


// SubDlgConfigWhiteList2 message handlers

void SubDlgConfigWhiteList2::OnBnClickedBtnPrevPage()
{
	if(m_uCurPageNum == 0)
		return;
	
	m_uCurPageNum--;

	iQueryPage();
}

void SubDlgConfigWhiteList2::OnBnClickedBtnNextPage()
{
	if( (m_uRows + g_PageSize*m_uCurPageNum) >= m_maxRows)
		return;
	
	m_uCurPageNum++;

	iQueryPage();
}

void SubDlgConfigWhiteList2::Refresh(){
	iGetCount();
	iQueryPage();
}

void SubDlgConfigWhiteList2::OnBnClickedBtnRefreshPage()
{
	Refresh();
}

void SubDlgConfigWhiteList2::OnBnClickedBtnLookup()
{
	Refresh();
}

BOOL SubDlgConfigWhiteList2::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_cListLPR.ModifyStyle(0, LVS_REPORT);
	m_cListLPR.SetExtendedStyle(m_cListLPR.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);
	m_cListLPR.InsertColumn(0, "启用");
	m_cListLPR.InsertColumn(1, "车牌号");
	m_cListLPR.InsertColumn(2, "生效时间");
	m_cListLPR.InsertColumn(3, "过期时间");
	m_cListLPR.InsertColumn(4, "启用时间段");
	m_cListLPR.InsertColumn(5, "黑名单");
	m_cListLPR.InsertColumn(6, "姓名");
	m_cListLPR.InsertColumn(7, "身份码");
	m_cListLPR.InsertColumn(8, "客户编号");
	m_cListLPR.InsertColumn(9, "车辆代码");
	m_cListLPR.InsertColumn(10, "车辆颜色");
	m_cListLPR.InsertColumn(11, "车牌类型");
	m_cListLPR.InsertColumn(12, "车牌备注");
	CRect rect; 
	m_cListLPR.GetClientRect(rect);
	int nUnitW = rect.Width()/38;
	m_cListLPR.SetColumnWidth(0, nUnitW*2);
	m_cListLPR.SetColumnWidth(1, nUnitW*3);
	m_cListLPR.SetColumnWidth(2, nUnitW*5);
	m_cListLPR.SetColumnWidth(3, nUnitW*3);
	m_cListLPR.SetColumnWidth(4, nUnitW*3);
	m_cListLPR.SetColumnWidth(5, nUnitW*2);
	m_cListLPR.SetColumnWidth(6, nUnitW*3);
	m_cListLPR.SetColumnWidth(7, nUnitW*3);
	m_cListLPR.SetColumnWidth(8, nUnitW*3);
	m_cListLPR.SetColumnWidth(9, nUnitW*3);
	m_cListLPR.SetColumnWidth(10, nUnitW*3);
	m_cListLPR.SetColumnWidth(11, nUnitW*5);
	m_cListLPR.SetColumnWidth(12, nUnitW*3);

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

void SubDlgConfigWhiteList2::GetColorValue(int iValue,char* strValue){
	for(int i=0;i<m_maxVehicleColorCount;i++){
		if(iValue == m_VehicleColorValues[i].iValue){
			strcpy_s(strValue,VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN, m_VehicleColorValues[i].strValue);
			return;
		}
	}
}

void SubDlgConfigWhiteList2::GetColorIndex(int &iValue,const char* strValue){
	for(int i=0;i<m_maxVehicleColorCount;i++){
		if(strcmp(strValue,m_VehicleColorValues[i].strValue)==0){
			iValue = i;
			return;
		}
	}
}


void SubDlgConfigWhiteList2::GetPlateTypeValue(int iValue,char* strValue){
	for(int i=0;i<m_maxPlateTypeCount;i++){
		if(iValue == m_PlateTypeValues[i].iValue){
			strcpy_s(strValue,VZ_LPR_WLIST_MAX_ENUM_VALUE_LEN, m_PlateTypeValues[i].strValue);
			return;
		}
	}
}

void SubDlgConfigWhiteList2::GetPlateTypeIndex(int &iValue,const char* strValue){
	for(int i=0;i<m_maxPlateTypeCount;i++){
		if(strcmp(strValue,m_PlateTypeValues[i].strValue)==0){
			iValue = i;
			return;
		}
	}
}


void SubDlgConfigWhiteList2::iOnQueryLPRecord(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP,
										  const VZ_LPR_WLIST_CUSTOMER *pCustomer)
{
	if(m_uRows == g_PageSize)
			return;
	char str[64];
	m_cListLPR.InsertItem(m_uRows, "");
	if(pLP)
	{			
		sprintf_s(str, 64, "%u", pLP->bEnable);
		m_cListLPR.SetItemText(m_uRows, 0, str);
		
		m_cListLPR.SetItemText(m_uRows, 1, pLP->strPlateID);
		
		if(pLP->bEnableTMEnable){
			sprintf_s(str, 64, "%04d-%02d-%02d %02d:%02d:%02d", 
				pLP->struTMEnable.nYear, pLP->struTMEnable.nMonth, pLP->struTMEnable.nMDay,
				pLP->struTMEnable.nHour, pLP->struTMEnable.nMin, pLP->struTMEnable.nSec);
			m_cListLPR.SetItemText(m_uRows, 2, str);
		}

		if(pLP->bEnableTMOverdule){
			sprintf_s(str, 64, "%04d-%02d-%02d %02d:%02d:%02d", 
				pLP->struTMOverdule.nYear, pLP->struTMOverdule.nMonth, pLP->struTMOverdule.nMDay,
				pLP->struTMOverdule.nHour, pLP->struTMOverdule.nMin, pLP->struTMOverdule.nSec);
			m_cListLPR.SetItemText(m_uRows, 3, str);
		}
		
		sprintf_s(str, 64, "%u", pLP->bUsingTimeSeg);
		m_cListLPR.SetItemText(m_uRows, 4, str);
		
		sprintf_s(str, 64, "%u", pLP->bAlarm);
		m_cListLPR.SetItemText(m_uRows, 5, str);

		m_cListLPR.SetItemText(m_uRows, 9, pLP->strCode);

		GetColorValue(pLP->iColor,str);
		m_cListLPR.SetItemText(m_uRows, 10, str);

		GetPlateTypeValue(pLP->iPlateType,str);
		m_cListLPR.SetItemText(m_uRows, 11, str);

		m_cListLPR.SetItemText(m_uRows, 12, pLP->strComment);

		CopyVehicle(pLP,&m_pLPRecords[m_uRows]);
	}
	
	if(pCustomer)
	{
		m_cListLPR.SetItemText(m_uRows, 6, pCustomer->strName);
		m_cListLPR.SetItemText(m_uRows, 7, pCustomer->strCode);
		sprintf_s(str, 64, "%u", pCustomer->uCustomerID);
		m_cListLPR.SetItemText(m_uRows, 8, str);
		
		memcpy(m_pLPCustomers + m_uRows, pCustomer, sizeof(VZ_LPR_WLIST_CUSTOMER));
	}
	m_uRows++;
}


void SubDlgConfigWhiteList2::iQueryPage()
{
	m_uRows = 0;

	m_cListLPR.DeleteAllItems();

	VZ_LPR_WLIST_LOAD_CONDITIONS conditions;
	VZ_LPR_WLIST_LIMIT limit;
	limit.limitType = VZ_LPR_WLIST_LIMIT_TYPE_RANGE;
	VZ_LPR_WLIST_RANGE_LIMIT range;
	range.startIndex = m_uCurPageNum*g_PageSize;
	range.count = g_PageSize;
	limit.pRangeLimit = &range;
	conditions.pLimit = &limit;
	conditions.pSortType = NULL;
	conditions.pSearchWhere = &m_searchWhere;

	VzLPRClient_WhiteListLoadRow(m_hLPRC,
		&conditions);

	//iCleanEdit();
}

void SubDlgConfigWhiteList2::iGetCount()
{
	VzLPRClient_WhiteListGetRowCount(m_hLPRC,&m_maxRows,
	&m_searchWhere);
}

void SubDlgConfigWhiteList2::OnEnChangeEditLookup()
{
	char buffer[256];
	GetDlgItemText(IDC_EDIT_LOOKUP, buffer,256);
	if(strlen(buffer) != 0){
		VZ_LPR_WLIST_SEARCH_CONSTRAINT *pSearchConstraints = new VZ_LPR_WLIST_SEARCH_CONSTRAINT[15];
		m_searchWhere.pSearchConstraints = pSearchConstraints;
		m_searchWhere.searchConstraintCount = 15;
		strcpy_s(m_searchWhere.pSearchConstraints[0].key, sizeof(m_searchWhere.pSearchConstraints[0].key), VZLPRC_WLIST_COL_TIME_ON_CREATE);
		strcpy_s(m_searchWhere.pSearchConstraints[1].key, sizeof(m_searchWhere.pSearchConstraints[1].key), VZLPRC_WLIST_COL_TIME_OVERDUE);
		strcpy_s(m_searchWhere.pSearchConstraints[2].key, sizeof(m_searchWhere.pSearchConstraints[2].key), VZLPRC_WLIST_COL_ENABLE);
		strcpy_s(m_searchWhere.pSearchConstraints[3].key, sizeof(m_searchWhere.pSearchConstraints[3].key), VZLPRC_WLIST_COL_LP);
		strcpy_s(m_searchWhere.pSearchConstraints[4].key, sizeof(m_searchWhere.pSearchConstraints[4].key), VZLPRC_WLIST_COL_TIME_SEG);
		strcpy_s(m_searchWhere.pSearchConstraints[5].key, sizeof(m_searchWhere.pSearchConstraints[5].key), VZLPRC_WLIST_COL_NEED_ALARM);
		strcpy_s(m_searchWhere.pSearchConstraints[6].key, sizeof(m_searchWhere.pSearchConstraints[6].key), VZLPRC_WLIST_COL_CUSTOM_ID);
		strcpy_s(m_searchWhere.pSearchConstraints[7].key, sizeof(m_searchWhere.pSearchConstraints[7].key), VZLPRC_WLIST_COL_VEHICLE_ID);
		strcpy_s(m_searchWhere.pSearchConstraints[8].key, sizeof(m_searchWhere.pSearchConstraints[8].key), VZLPRC_WLIST_COL_CUSTOM_IDCODE);
		strcpy_s(m_searchWhere.pSearchConstraints[9].key, sizeof(m_searchWhere.pSearchConstraints[9].key), VZLPRC_WLIST_COL_CUSTOM_NAME);
		strcpy_s(m_searchWhere.pSearchConstraints[10].key, sizeof(m_searchWhere.pSearchConstraints[10].key), VZLPRC_WLIST_COL_CUSTOM_ID);
		strcpy_s(m_searchWhere.pSearchConstraints[11].key, sizeof(m_searchWhere.pSearchConstraints[11].key), VZLPRC_WLIST_COL_VEHICLE_IDCODE);
		strcpy_s(m_searchWhere.pSearchConstraints[12].key, sizeof(m_searchWhere.pSearchConstraints[12].key), VZLPRC_WLIST_COL_VEHICLE_COLOR);
		strcpy_s(m_searchWhere.pSearchConstraints[13].key, sizeof(m_searchWhere.pSearchConstraints[13].key), VZLPRC_WLIST_COL_VEHICLE_PLATETYPE);
		strcpy_s(m_searchWhere.pSearchConstraints[14].key, sizeof(m_searchWhere.pSearchConstraints[14].key), VZLPRC_WLIST_COL_VEHICLE_COMMENT);
		for(size_t i=0;i<m_searchWhere.searchConstraintCount;i++){
			strcpy_s(m_searchWhere.pSearchConstraints[i].search_string , sizeof(m_searchWhere.pSearchConstraints[i].search_string), buffer);
		}
	}
	else{
		if(m_searchWhere.pSearchConstraints != NULL){
			delete m_searchWhere.pSearchConstraints;
			m_searchWhere.pSearchConstraints = NULL;
		}
		m_searchWhere.searchConstraintCount = 0;
	}
	iQueryPage();
}

void SubDlgConfigWhiteList2::OnBnClickedCleardb()
{
	VzLPRClient_WhiteListClearCustomersAndVehicles(m_hLPRC);
	Refresh();
}

static int iListCtrlGetCurSel(CListCtrl *pList)
{
	int nCurrSel = -1;
	POSITION pos = pList->GetFirstSelectedItemPosition();
	while(pos)
	{
		nCurrSel = pList->GetNextSelectedItem(pos);
	}

	return(nCurrSel);
}

void SubDlgConfigWhiteList2::OnBnClickedDeleteCustomerAndVehicles()
{
	int nCurrSel = iListCtrlGetCurSel(&m_cListLPR);
	if(nCurrSel<0){
		return;
	}
	int customerID = m_pLPCustomers[nCurrSel].uCustomerID;
	VzLPRClient_WhiteListDeleteCustomerAndVehiclesByCustomerID(m_hLPRC,customerID);
	iQueryPage();
}

