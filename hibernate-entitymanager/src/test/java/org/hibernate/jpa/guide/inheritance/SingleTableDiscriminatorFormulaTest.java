/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.guide.inheritance;

import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.dialect.Oracle10gDialect;
import org.hibernate.dialect.PostgreSQL81Dialect;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.hibernate.testing.RequiresDialect;
import org.junit.Test;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;

import static org.hibernate.jpa.test.util.TransactionUtil.*;

/**
 * @author Vlad Mihalcea
 */
public class SingleTableDiscriminatorFormulaTest extends BaseEntityManagerFunctionalTestCase {

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class<?>[]{
                DebitAccount.class,
                CreditAccount.class,
        };
    }

    @Test
    @RequiresDialect( { PostgreSQL81Dialect.class })
    public void test() {
        doInJPA(this::entityManagerFactory, entityManager -> {
            DebitAccount debitAccount = new DebitAccount("123-debit");
            debitAccount.setId(1L);
            debitAccount.setOwner("John Doe");
            debitAccount.setBalance(BigDecimal.valueOf(100));
            debitAccount.setInterestRate(BigDecimal.valueOf(1.5d));
            debitAccount.setOverdraftFee(BigDecimal.valueOf(25));

            CreditAccount creditAccount = new CreditAccount("456-credit");
            creditAccount.setId(2L);
            creditAccount.setOwner("John Doe");
            creditAccount.setBalance(BigDecimal.valueOf(1000));
            creditAccount.setInterestRate(BigDecimal.valueOf(1.9d));
            creditAccount.setCreditLimit(BigDecimal.valueOf(5000));

            entityManager.persist(debitAccount);
            entityManager.persist(creditAccount);
        });

        doInJPA(this::entityManagerFactory, entityManager -> {
            List<Account> accounts =
                entityManager.createQuery("select a from Account a").getResultList();
            assertEquals(2, accounts.size());
        });
    }

    @Entity(name = "Account")
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    @DiscriminatorFormula(
        "case when debitKey is not null " +
        "then 'Debit' " +
        "else ( " +
        "   case when creditKey is not null " +
        "   then 'Credit' " +
        "   else 'Unknown' " +
        "   end ) " +
        "end "
    )
    public static class Account {

        @Id
        private Long id;

        private String owner;

        private BigDecimal balance;

        private BigDecimal interestRate;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }

        public BigDecimal getInterestRate() {
            return interestRate;
        }

        public void setInterestRate(BigDecimal interestRate) {
            this.interestRate = interestRate;
        }
    }

    @Entity(name = "DebitAccount")
    @DiscriminatorValue(value = "Debit")
    public static class DebitAccount extends Account {

        private String debitKey;

        private BigDecimal overdraftFee;

        private DebitAccount() {}

        public DebitAccount(String debitKey) {
            this.debitKey = debitKey;
        }

        public String getDebitKey() {
            return debitKey;
        }

        public BigDecimal getOverdraftFee() {
            return overdraftFee;
        }

        public void setOverdraftFee(BigDecimal overdraftFee) {
            this.overdraftFee = overdraftFee;
        }
    }

    @Entity(name = "CreditAccount")
    @DiscriminatorValue(value = "Credit")
    public static class CreditAccount extends Account {

        private String creditKey;

        private BigDecimal creditLimit;

        private CreditAccount() {}

        public CreditAccount(String creditKey) {
            this.creditKey = creditKey;
        }

        public String getCreditKey() {
            return creditKey;
        }

        public BigDecimal getCreditLimit() {
            return creditLimit;
        }

        public void setCreditLimit(BigDecimal creditLimit) {
            this.creditLimit = creditLimit;
        }
    }
}
