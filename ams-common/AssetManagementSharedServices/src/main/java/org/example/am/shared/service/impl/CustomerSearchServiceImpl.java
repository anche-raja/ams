package org.example.am.shared.service.impl;

import java.util.List;

import org.example.am.shared.dao.CustomerSearchDAO;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.service.CustomerSearchService;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("customerSearchService")
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class CustomerSearchServiceImpl implements CustomerSearchService {

    @Autowired
    private CustomerSearchDAO customerSearchDAO;

    @Override
    public List<Customer> search(final String term) {
        return search(term, CommonConstants.MAX_SEARCH_RESULTS);
    }

    /**
     * An all-digit term is treated as an account number; anything else is a name fragment. This is
     * what lets the single search box on the customer picker serve both.
     */
    @Override
    public List<Customer> search(final String term, final int maxRows) {
        if (term == null || term.trim().length() == 0) {
            return java.util.Collections.<Customer>emptyList();
        }
        final String trimmed = term.trim();
        if (isAllDigits(trimmed)) {
            final List<Customer> byAccountNumber =
                    customerSearchDAO.searchByAccountNumber(trimmed, maxRows);
            if (!byAccountNumber.isEmpty()) {
                return byAccountNumber;
            }
        }
        return customerSearchDAO.searchByName(trimmed, maxRows);
    }

    @Override
    public List<Customer> listAll(final int maxRows) {
        return customerSearchDAO.listCustomers(maxRows);
    }

    private static boolean isAllDigits(final String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return value.length() > 0;
    }

    public void setCustomerSearchDAO(final CustomerSearchDAO customerSearchDAO) {
        this.customerSearchDAO = customerSearchDAO;
    }
}
